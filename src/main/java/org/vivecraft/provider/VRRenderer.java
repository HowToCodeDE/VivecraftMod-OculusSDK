package org.vivecraft.provider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.opengl.ARBShaderObjects;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;
import org.vivecraft.gameplay.screenhandlers.GuiHandler;
import org.vivecraft.gameplay.screenhandlers.KeyboardHandler;
import org.vivecraft.gameplay.screenhandlers.RadialHandler;
import org.vivecraft.gameplay.trackers.TelescopeTracker;
import org.vivecraft.render.RenderConfigException;
import org.vivecraft.render.RenderPass;
import org.vivecraft.render.ShaderHelper;
import org.vivecraft.render.VRShaders;
import org.vivecraft.settings.VRSettings;
import org.vivecraft.utils.LangHelper;

import com.example.examplemod.DataHolder;
import com.example.examplemod.GameRendererExtension;
import com.example.examplemod.GlStateHelper;
import com.example.examplemod.RenderTargetExtension;
import com.example.examplemod.MethodHolder;
import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Matrix4f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraft.world.level.dimension.DimensionType;
//import net.optifine.shaders.Shaders;

//TODO
public abstract class VRRenderer
{
    public static final String RENDER_SETUP_FAILURE_MESSAGE = "Failed to initialise stereo rendering plugin: ";
    public Map<String, PostChain> alphaShaders = new HashMap<>();
    public RenderTarget cameraFramebuffer;
    public RenderTarget cameraRenderFramebuffer;
    protected int dispLastWidth;
    protected int dispLastHeight;
    public Map<String, PostChain> entityShaders = new HashMap<>();
    public Matrix4f[] eyeproj = new Matrix4f[2];
    public RenderTarget framebufferEye0;
    public RenderTarget framebufferEye1;
    public RenderTarget framebufferMR;
    public RenderTarget framebufferUndistorted;
    public RenderTarget framebufferVrRender;
    public RenderTarget fsaaFirstPassResultFBO;
    public RenderTarget fsaaLastPassResultFBO;
    protected float[][] hiddenMesheVertecies = new float[2][];
    public ResourceKey<DimensionType> lastDimensionId = DimensionType.OVERWORLD_LOCATION;
    public int lastDisplayFBHeight = 0;
    public int lastDisplayFBWidth = 0;
    public boolean lastEnableVsync = true;
    public boolean lastFogFancy = true;
    public boolean lastFogFast = false;
    public int lastGuiScale = 0;
    protected VRSettings.MirrorMode lastMirror;
    public int lastRenderDistanceChunks = -1;
    public long lastWindow = 0L;
    public float lastWorldScale = 0.0F;
    protected int LeftEyeTextureId = -1;
    protected int RightEyeTextureId = -1;
    public int mirrorFBHeight;
    public int mirrorFBWidth;
    protected boolean reinitFramebuffers = true;
    public boolean reinitShadersFlag = false;
    public float renderScale;
    protected Tuple<Integer, Integer> resolution;
    public float ss = -1.0F;
    public RenderTarget telescopeFramebufferL;
    public RenderTarget telescopeFramebufferR;
    protected MCVR vr;

    public VRRenderer(MCVR vr)
    {
        this.vr = vr;
    }

    protected void checkGLError(String message)
    {
        //Config.checkGlError(message); TODO
    }

    public boolean clipPlanesChanged()
    {
        return false;
    }

    public abstract void createRenderTexture(int var1, int var2);

    public abstract Matrix4f getProjectionMatrix(int var1, float var2, float var3);

    public abstract void endFrame() throws RenderConfigException;

    public abstract boolean providesStencilMask();

    protected PostChain createShaderGroup(ResourceLocation resource, RenderTarget fb) throws JsonSyntaxException, IOException
    {
        Minecraft minecraft = Minecraft.getInstance();
        PostChain postchain = new PostChain(minecraft.getTextureManager(), minecraft.getResourceManager(), fb, resource);
        postchain.resize(fb.viewWidth, fb.viewHeight);
        return postchain;
    }

    public void deleteRenderTextures()
    {
        if (this.LeftEyeTextureId > 0)
        {
            GL11.glDeleteTextures(this.LeftEyeTextureId);
        }

        if (this.RightEyeTextureId > 0)
        {
            GL11.glDeleteTextures(this.RightEyeTextureId);
        }

        this.LeftEyeTextureId = this.RightEyeTextureId = -1;
    }

    public void doStencil(boolean inverse)
    {
        Minecraft minecraft = Minecraft.getInstance();
        DataHolder dataholder = DataHolder.getInstance();
        
        //setup stencil for writing
        GL11.glEnable(GL11.GL_STENCIL_TEST);
		GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
		GL11.glStencilMask(0xFF); // Write to stencil buffer
        
		if(inverse) {
			//clear whole image for total mask in color, stencil, depth
			GL11.glClearStencil(0xFF);
	    	GL43.glClearDepthf(0);

			GL11.glStencilFunc(GL11.GL_ALWAYS, 0, 0xFF); // Set any stencil to 0	
    		RenderSystem.colorMask(false, false, false, true); 

		} else {
			//clear whole image for total transparency
			GL11.glClearStencil(0);
	    	GL43.glClearDepthf(1);
	       
			GL11.glStencilFunc(GL11.GL_ALWAYS, 0xFF, 0xFF); // Set any stencil to 1
    		RenderSystem.colorMask(true, true, true, true); 
		}
		
		GlStateHelper.clear(GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT); 
		
		GL11.glClearStencil(0);
    	GL43.glClearDepthf(1);
   	
		RenderSystem.depthMask(true); 
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.disableTexture();
        RenderSystem.disableCull();
        
        GL43.glColor4f(0F, 0F, 0F, 1.0F);
        
        GL43.glMatrixMode(GL11.GL_PROJECTION);
        GL43.glPushMatrix();
        GL43.glLoadIdentity();
        RenderTarget fb = minecraft.getMainRenderTarget();
        RenderSystem.viewport(0, 0, fb.viewWidth, fb.viewHeight);
        GL43.glOrtho(0.0D, (double)fb.viewWidth, 0.0D, (double)fb.viewHeight, 0.0, 20.0D);
        GL43.glMatrixMode(GL11.GL_MODELVIEW);
        GL43.glPushMatrix();
        GL43.glLoadIdentity();
        if(inverse) //draw on far clip
        	GL43.glTranslatef(0, 0, -20);
        int s= GL43.glGetInteger(GL43.GL_CURRENT_PROGRAM);
        GL30.glUseProgram(0);
        
        if(dataholder.currentPass == RenderPass.SCOPEL || dataholder.currentPass == RenderPass.SCOPER){
            drawCircle(fb.viewWidth, fb.viewHeight);
        } else if(dataholder.currentPass == RenderPass.LEFT||dataholder.currentPass == RenderPass.RIGHT) {
        	drawMask();
        }

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL43.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL43.glPopMatrix();

        RenderSystem.depthMask(true); // Do write to depth buffer
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.enableDepthTest();

        GL43.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);      
        RenderSystem.enableTexture();
        RenderSystem.enableCull();
        GL30.glUseProgram(s);
        GL11.glStencilFunc(GL11.GL_NOTEQUAL, 255, 1);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        GL11.glStencilMask(0); // Dont Write to stencil buffer
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
    }

    public void doFSAA(boolean hasShaders)
    {
        if (this.fsaaFirstPassResultFBO == null)
        {
            this.reinitFrameBuffers("FSAA Setting Changed");
        }
        else
        {
            //GlStateManager.disableAlphaTest(); 
            GlStateManager._disableBlend();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL43.glPushMatrix();
            GL43.glLoadIdentity();
            GL43.glMatrixMode(5888);
            GL43.glPushMatrix();
            GL43.glLoadIdentity();
            GL11.glTranslatef(0.0F, 0.0F, -0.7F);
            this.fsaaFirstPassResultFBO.bindWrite(true);
            GlStateManager._activeTexture(33985);
            this.framebufferVrRender.bindRead();
            GlStateManager._activeTexture(33986);

//            if (hasShaders && Shaders.dfb != null) TODO
//            {
//                GlStateManager._bindTexture(Shaders.dfb.depthTextures.get(0));
//            }
//            else
            {
                GlStateManager._bindTexture(this.framebufferVrRender.frameBufferId); //TODO
            }

            GlStateManager._activeTexture(33984);
            GlStateManager._clearColor(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager._clearDepth(1.0D);
            GlStateHelper.clear(16640);
            GlStateManager._viewport(0, 0, this.fsaaFirstPassResultFBO.viewWidth, this.fsaaFirstPassResultFBO.viewHeight);
            GlStateManager._glUseProgram(VRShaders._Lanczos_shaderProgramId);
            ARBShaderObjects.glUniform1fARB(VRShaders._Lanczos_texelWidthOffsetUniform, 1.0F / (3.0F * (float)this.fsaaFirstPassResultFBO.viewWidth));
            ARBShaderObjects.glUniform1fARB(VRShaders._Lanczos_texelHeightOffsetUniform, 0.0F);
            ARBShaderObjects.glUniform1iARB(VRShaders._Lanczos_inputImageTextureUniform, 1);
            ARBShaderObjects.glUniform1iARB(VRShaders._Lanczos_inputDepthTextureUniform, 2);
            GlStateHelper.clear(16384); 
            this.drawQuad();
            this.fsaaLastPassResultFBO.bindWrite(true);
            GlStateManager._activeTexture(33985);
            this.fsaaFirstPassResultFBO.bindRead();
            GlStateManager._activeTexture(33986);
            GlStateManager._bindTexture(this.fsaaFirstPassResultFBO.frameBufferId); //TODO
            GlStateManager._activeTexture(33984);
            this.checkGLError("posttex");
            GlStateManager._viewport(0, 0, this.fsaaLastPassResultFBO.viewWidth, this.fsaaLastPassResultFBO.viewHeight);
            GlStateManager._clearColor(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager._clearDepth(1.0D);
            GlStateHelper.clear(16640); 
            this.checkGLError("postclear");
            GlStateManager._activeTexture(33984);
            this.checkGLError("postact");
            ARBShaderObjects.glUniform1fARB(VRShaders._Lanczos_texelWidthOffsetUniform, 0.0F);
            ARBShaderObjects.glUniform1fARB(VRShaders._Lanczos_texelHeightOffsetUniform, 1.0F / (3.0F * (float)this.framebufferEye0.viewHeight));
            ARBShaderObjects.glUniform1iARB(VRShaders._Lanczos_inputImageTextureUniform, 1);
            ARBShaderObjects.glUniform1iARB(VRShaders._Lanczos_inputDepthTextureUniform, 2);
            this.drawQuad();
            this.checkGLError("postdraw");
            GlStateManager._glUseProgram(0);
            GlStateHelper.enableAlphaTest(); 
            GlStateManager._enableBlend();
            GL43.glMatrixMode(5889);
            GL43.glPopMatrix();
            GL43.glMatrixMode(5888);
            GL43.glPopMatrix();
        }
    }
    
    private void drawCircle(float width, float height) {
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        int i = 32;
        float f = (float)(width / 2);
        GL11.glVertex2f((float)(width / 2), (float)(width / 2));
        for (int j = 0; j < i + 1; ++j)
        {
            float f1 = (float)j / (float)i * (float)Math.PI * 2.0F;
            float f2 = (float)((double)(width / 2) + Math.cos((double)f1) * (double)f);
            float f3 = (float)((double)(width / 2) + Math.sin((double)f1) * (double)f);
            GL11.glVertex2f(f2, f3);
        }
        GL11.glEnd();
    }
    
    private void drawMask() {
		Minecraft mc = Minecraft.getInstance();
		DataHolder dh = DataHolder.getInstance();
		float[] verts = getStencilMask(dh.currentPass);
		if (verts == null) return;
		
        GL11.glBegin(GL11.GL_TRIANGLES);

        for (int i = 0; i < verts.length; i += 2)
        {
            GL11.glVertex2f(verts[i] * dh.vrRenderer.renderScale, verts[i + 1] * dh.vrRenderer.renderScale);
        }

        GL11.glEnd();
    }

    private void drawQuad()
    {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0F, 0.0F);
        GL11.glVertex3f(-1.0F, -1.0F, 0.0F);
        GL11.glTexCoord2f(1.0F, 0.0F);
        GL11.glVertex3f(1.0F, -1.0F, 0.0F);
        GL11.glTexCoord2f(1.0F, 1.0F);
        GL11.glVertex3f(1.0F, 1.0F, 0.0F);
        GL11.glTexCoord2f(0.0F, 1.0F);
        GL11.glVertex3f(-1.0F, 1.0F, 0.0F);
        GL11.glEnd();
    }

    public double getCurrentTimeSecs()
    {
        return (double)System.nanoTime() / 1.0E9D;
    }

    public double getFrameTiming()
    {
        return this.getCurrentTimeSecs();
    }

    public String getinitError()
    {
        return this.vr.initStatus;
    }

    public String getLastError()
    {
        return "";
    }

    public String getName()
    {
        return "OpenVR";
    }

    public List<RenderPass> getRenderPasses()
    {
        Minecraft minecraft = Minecraft.getInstance();
        DataHolder dataholder = DataHolder.getInstance();
        List<RenderPass> list = new ArrayList<>();
        list.add(RenderPass.LEFT);
        list.add(RenderPass.RIGHT);

        if (dataholder.vrSettings.displayMirrorMode == VRSettings.MirrorMode.FIRST_PERSON)
        {
            list.add(RenderPass.CENTER);
        }
        else if (dataholder.vrSettings.displayMirrorMode == VRSettings.MirrorMode.MIXED_REALITY)
        {
            if (dataholder.vrSettings.mixedRealityUndistorted && dataholder.vrSettings.mixedRealityUnityLike)
            {
                list.add(RenderPass.CENTER);
            }

            list.add(RenderPass.THIRD);
        }
        else if (dataholder.vrSettings.displayMirrorMode == VRSettings.MirrorMode.THIRD_PERSON)
        {
            list.add(RenderPass.THIRD);
        }

        if (minecraft.player != null)
        {
            if (TelescopeTracker.isTelescope(minecraft.player.getMainHandItem()) && TelescopeTracker.isViewing(0))
            {
                list.add(RenderPass.SCOPER);
            }

            if (TelescopeTracker.isTelescope(minecraft.player.getOffhandItem()) && TelescopeTracker.isViewing(1))
            {
                list.add(RenderPass.SCOPEL);
            }

            if (dataholder.cameraTracker.isVisible())
            {
                list.add(RenderPass.CAMERA);
            }
        }

        return list;
    }

    public abstract Tuple<Integer, Integer> getRenderTextureSizes();

    public float[] getStencilMask(RenderPass eye)
    {
        if (this.hiddenMesheVertecies != null && (eye == RenderPass.LEFT || eye == RenderPass.RIGHT))
        {
            return eye == RenderPass.LEFT ? this.hiddenMesheVertecies[0] : this.hiddenMesheVertecies[1];
        }
        else
        {
            return null;
        }
    }

    public boolean isInitialized()
    {
        return this.vr.initSuccess;
    }

    public void reinitFrameBuffers(String cause)
    {
        this.reinitFramebuffers = true;
        System.out.println("Reinit Render: " + cause);
    }

    public void setupRenderConfiguration() throws Exception
    {
        Minecraft minecraft = Minecraft.getInstance();
        DataHolder dataholder = DataHolder.getInstance();
        boolean flag = false;

        if (this.clipPlanesChanged())
        {
            this.reinitFrameBuffers("Clip Planes Changed");
        }

        if (minecraft.getWindow().getWindow() != this.lastWindow)
        {
            this.lastWindow = minecraft.getWindow().getWindow();
            this.reinitFrameBuffers("Window Handle Changed");
        }

        if (this.lastEnableVsync != minecraft.options.enableVsync)
        {
            this.reinitFrameBuffers("VSync Changed");
            this.lastEnableVsync = minecraft.options.enableVsync;
        }

        if (this.lastMirror != dataholder.vrSettings.displayMirrorMode)
        {
            this.reinitFrameBuffers("Mirror Changed");
            this.lastMirror = dataholder.vrSettings.displayMirrorMode;
        }

        if (this.reinitFramebuffers)
        {
            this.reinitShadersFlag = true;
            this.checkGLError("Start Init");
            int i = minecraft.getWindow().getScreenWidth() < 1 ? 1 : minecraft.getWindow().getScreenWidth();
            int j = minecraft.getWindow().getScreenHeight() < 1 ? 1 : minecraft.getWindow().getScreenHeight();

//            if (Config.openGlRenderer.toLowerCase().contains("intel")) TODO
//            {
//                throw new RenderConfigException("Incompatible", LangHelper.get("vivecraft.messages.intelgraphics", Config.openGlRenderer));
//            }

            if (!this.isInitialized())
            {
                throw new RenderConfigException("Failed to initialise stereo rendering plugin: " + this.getName(), LangHelper.get(this.getinitError()));
            }

            Tuple<Integer, Integer> tuple = this.getRenderTextureSizes();
            int eyew = tuple.getA();
            int eyeh = tuple.getB();

            if (this.framebufferVrRender != null)
            {
                this.framebufferVrRender.destroyBuffers();
                this.framebufferVrRender = null;
            }

            if (this.framebufferMR != null)
            {
                this.framebufferMR.destroyBuffers();
                this.framebufferMR = null;
            }

            if (this.framebufferUndistorted != null)
            {
                this.framebufferUndistorted.destroyBuffers();
                this.framebufferUndistorted = null;
            }

            if (GuiHandler.guiFramebuffer != null)
            {
                GuiHandler.guiFramebuffer.destroyBuffers();
                GuiHandler.guiFramebuffer = null;
            }

            if (KeyboardHandler.Framebuffer != null)
            {
                KeyboardHandler.Framebuffer.destroyBuffers();
                KeyboardHandler.Framebuffer = null;
            }

            if (RadialHandler.Framebuffer != null)
            {
                RadialHandler.Framebuffer.destroyBuffers();
                RadialHandler.Framebuffer = null;
            }

            if (this.telescopeFramebufferL != null)
            {
                this.telescopeFramebufferL.destroyBuffers();
                this.telescopeFramebufferL = null;
            }

            if (this.telescopeFramebufferR != null)
            {
                this.telescopeFramebufferR.destroyBuffers();
                this.telescopeFramebufferR = null;
            }

            if (this.cameraFramebuffer != null)
            {
                this.cameraFramebuffer.destroyBuffers();
                this.cameraFramebuffer = null;
            }

            if (this.cameraRenderFramebuffer != null)
            {
                this.cameraRenderFramebuffer.destroyBuffers();
                this.cameraRenderFramebuffer = null;
            }

            if (this.fsaaFirstPassResultFBO != null)
            {
                this.fsaaFirstPassResultFBO.destroyBuffers();
                this.fsaaFirstPassResultFBO = null;
            }

            if (this.fsaaLastPassResultFBO != null)
            {
                this.fsaaLastPassResultFBO.destroyBuffers();
                this.fsaaLastPassResultFBO = null;
            }

            int i1 = 0;
            boolean flag1 = i1 > 0;

            if (this.LeftEyeTextureId == -1)
            {
                this.createRenderTexture(eyew, eyeh);

                if (this.LeftEyeTextureId == -1)
                {
                    throw new RenderConfigException("Failed to initialise stereo rendering plugin: " + this.getName(), this.getLastError());
                }

                dataholder.print("Provider supplied render texture IDs: " + this.LeftEyeTextureId + " " + this.RightEyeTextureId);
                dataholder.print("Provider supplied texture resolution: " + eyew + " x " + eyeh);
            }

            this.checkGLError("Render Texture setup");

            if (this.framebufferEye0 == null)
            {
                this.framebufferEye0 = MethodHolder.TextureTarget("L Eye", eyew, eyeh, false, false, this.LeftEyeTextureId, false, true);
                dataholder.print(this.framebufferEye0.toString());
                this.checkGLError("Left Eye framebuffer setup");
            }

            if (this.framebufferEye1 == null)
            {
                this.framebufferEye1 = MethodHolder.TextureTarget("R Eye", eyew, eyeh, false, false, this.RightEyeTextureId, false, true);
                dataholder.print(this.framebufferEye1.toString());
                this.checkGLError("Right Eye framebuffer setup");
            }

            this.renderScale = (float)Math.sqrt((double)dataholder.vrSettings.renderScaleFactor);
            i = (int)Math.ceil((double)((float)eyew * this.renderScale));
            j = (int)Math.ceil((double)((float)eyeh * this.renderScale));
            this.framebufferVrRender = MethodHolder.TextureTarget("3D Render", i, j, true, false, -1, true, true);
            dataholder.print(this.framebufferVrRender.toString());
            this.checkGLError("3D framebuffer setup");
            this.mirrorFBWidth = minecraft.getWindow().getScreenWidth();
            this.mirrorFBHeight = minecraft.getWindow().getScreenHeight();

            if (dataholder.vrSettings.displayMirrorMode == VRSettings.MirrorMode.MIXED_REALITY)
            {
                this.mirrorFBWidth = minecraft.getWindow().getScreenWidth() / 2;

                if (dataholder.vrSettings.mixedRealityUnityLike)
                {
                    this.mirrorFBHeight = minecraft.getWindow().getScreenHeight() / 2;
                }
            }

//            if (Config.isShaders()) TODO
//            {
//                this.mirrorFBWidth = i;
//                this.mirrorFBHeight = j;
//            }

            List<RenderPass> list = this.getRenderPasses();

            for (RenderPass renderpass : list)
            {
                System.out.println("Passes: " + renderpass.toString());
            }

            if (list.contains(RenderPass.THIRD))
            {
                this.framebufferMR = MethodHolder.TextureTarget("Mixed Reality Render", this.mirrorFBWidth, this.mirrorFBHeight, true, false, -1, true, false);
                dataholder.print(this.framebufferMR.toString());
                this.checkGLError("Mixed reality framebuffer setup");
            }

            if (list.contains(RenderPass.CENTER))
            {
                this.framebufferUndistorted = MethodHolder.TextureTarget("Undistorted View Render", this.mirrorFBWidth, this.mirrorFBHeight, true, false, -1, false, false);
                dataholder.print(this.framebufferUndistorted.toString());
                this.checkGLError("Undistorted view framebuffer setup");
            }

            GuiHandler.guiFramebuffer = MethodHolder.TextureTarget("GUI", minecraft.getWindow().getScreenWidth(), minecraft.getWindow().getScreenHeight(), true, false, -1, false, true);
            dataholder.print(GuiHandler.guiFramebuffer.toString());
            this.checkGLError("GUI framebuffer setup");
            KeyboardHandler.Framebuffer = MethodHolder.TextureTarget("Keyboard", minecraft.getWindow().getScreenWidth(), minecraft.getWindow().getScreenHeight(), true, false, -1, false, true);
            dataholder.print(KeyboardHandler.Framebuffer.toString());
            this.checkGLError("Keyboard framebuffer setup");
            RadialHandler.Framebuffer = MethodHolder.TextureTarget("Radial Menu", minecraft.getWindow().getScreenWidth(), minecraft.getWindow().getScreenHeight(), true, false, -1, false, true);
            dataholder.print(RadialHandler.Framebuffer.toString());
            this.checkGLError("Radial framebuffer setup");
            int j2 = 720;
            int k2 = 720;

//            if (Config.isShaders()) TODO
//            { 
//                j2 = i;
//                k2 = j;
//            }

            this.checkGLError("Mirror framebuffer setup");
            this.telescopeFramebufferR = MethodHolder.TextureTarget("TelescopeR", j2, k2, true, false, -1, true, false);
            dataholder.print(this.telescopeFramebufferR.toString());
            this.checkGLError("TelescopeR framebuffer setup");
            this.telescopeFramebufferR.setClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            this.telescopeFramebufferR.clear(Minecraft.ON_OSX);
            this.telescopeFramebufferL = MethodHolder.TextureTarget("TelescopeL", j2, k2, true, false, -1, true, false);
            dataholder.print(this.telescopeFramebufferL.toString());
            this.telescopeFramebufferL.setClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            this.telescopeFramebufferL.clear(Minecraft.ON_OSX);
            this.checkGLError("TelescopeL framebuffer setup");
            int j1 = Math.round(1920.0F * dataholder.vrSettings.handCameraResScale);
            int k1 = Math.round(1080.0F * dataholder.vrSettings.handCameraResScale);
            int l1 = j1;
            int i2 = k1;

//            if (Config.isShaders())
//            {
//                float f = (float)j1 / (float)k1;
//
//                if (f > (float)(i / j))
//                {
//                    j1 = i;
//                    k1 = Math.round((float)i / f);
//                }
//                else
//                {
//                    j1 = Math.round((float)j * f);
//                    k1 = j;
//                }
//
//                l1 = i;
//                i2 = j;
//            }

            this.cameraFramebuffer = MethodHolder.TextureTarget("Handheld Camera", j1, k1, true, false, -1, true, false);
            dataholder.print(this.cameraFramebuffer.toString());
            this.checkGLError("Camera framebuffer setup");
            this.cameraRenderFramebuffer = MethodHolder.TextureTarget("Handheld Camera Render", l1, i2, true, false, -1, true, true);
            dataholder.print(this.cameraRenderFramebuffer.toString());
            this.checkGLError("Camera render framebuffer setup");
            ((GameRendererExtension)minecraft.gameRenderer).setupClipPlanes();
            this.eyeproj[0] = this.getProjectionMatrix(0, ((GameRendererExtension)minecraft.gameRenderer).getMinClipDistance(), ((GameRendererExtension)minecraft.gameRenderer).getClipDistance());
            this.eyeproj[1] = this.getProjectionMatrix(1, ((GameRendererExtension)minecraft.gameRenderer).getMinClipDistance(), ((GameRendererExtension)minecraft.gameRenderer).getClipDistance());

            if (dataholder.vrSettings.useFsaa)
            {
                try
                {
                    this.checkGLError("pre FSAA FBO creation");
                    this.fsaaFirstPassResultFBO = MethodHolder.TextureTarget("FSAA Pass1 FBO", eyew, j, false, false, -1, false, false);
                    this.fsaaLastPassResultFBO = MethodHolder.TextureTarget("FSAA Pass2 FBO", eyew, eyeh, false, false, -1, false, false);
                    dataholder.print(this.fsaaFirstPassResultFBO.toString());
                    dataholder.print(this.fsaaLastPassResultFBO.toString());
                    this.checkGLError("FSAA FBO creation");
                    VRShaders.setupFSAA();
                    ShaderHelper.checkGLError("FBO init fsaa shader");
                }
                catch (Exception exception)
                {
                	dataholder.vrSettings.useFsaa = false;
                	dataholder.vrSettings.saveOptions();
                    System.out.println(exception.getMessage());
                    this.reinitFramebuffers = true;
                    return;
                }
            }

            try
            {
                minecraft.mainRenderTarget = this.framebufferVrRender;
                VRShaders.setupDepthMask();
                ShaderHelper.checkGLError("init depth shader");
                VRShaders.setupFOVReduction();
                ShaderHelper.checkGLError("init FOV shader");
                List<PostChain> list1 = new ArrayList<>();
                list1.addAll(this.entityShaders.values());
                this.entityShaders.clear();
                ResourceLocation resourcelocation = new ResourceLocation("shaders/post/entity_outline.json");
                this.entityShaders.put(((RenderTargetExtension)this.framebufferVrRender).getName(), this.createShaderGroup(resourcelocation, this.framebufferVrRender));

                if (list.contains(RenderPass.THIRD))
                {
                    this.entityShaders.put(((RenderTargetExtension)this.framebufferMR).getName(), this.createShaderGroup(resourcelocation, this.framebufferMR));
                }

                if (list.contains(RenderPass.CENTER))
                {
                    this.entityShaders.put(((RenderTargetExtension)this.framebufferMR).getName(), this.createShaderGroup(resourcelocation, this.framebufferUndistorted));
                }

                this.entityShaders.put(((RenderTargetExtension)this.telescopeFramebufferL).getName(), this.createShaderGroup(resourcelocation, this.telescopeFramebufferL));
                this.entityShaders.put(((RenderTargetExtension)this.telescopeFramebufferR).getName(), this.createShaderGroup(resourcelocation, this.telescopeFramebufferR));
                this.entityShaders.put(((RenderTargetExtension)this.cameraRenderFramebuffer).getName(), this.createShaderGroup(resourcelocation, this.cameraRenderFramebuffer));

                for (PostChain postchain : list1)
                {
                    postchain.close();
                }

                list1.clear();
                list1.addAll(this.alphaShaders.values());
                this.alphaShaders.clear();

                if (Minecraft.useShaderTransparency())
                {
                    ResourceLocation resourcelocation1 = new ResourceLocation("shaders/post/vrtransparency.json");
                    this.alphaShaders.put(((RenderTargetExtension)this.framebufferVrRender).getName(), this.createShaderGroup(resourcelocation1, this.framebufferVrRender));

                    if (list.contains(RenderPass.THIRD))
                    {
                        this.alphaShaders.put(((RenderTargetExtension)this.framebufferMR).getName(), this.createShaderGroup(resourcelocation1, this.framebufferMR));
                    }

                    if (list.contains(RenderPass.CENTER))
                    {
                        this.alphaShaders.put(((RenderTargetExtension)this.framebufferMR).getName(), this.createShaderGroup(resourcelocation, this.framebufferMR));
                    }

                    this.alphaShaders.put(((RenderTargetExtension)this.telescopeFramebufferL).getName(), this.createShaderGroup(resourcelocation, this.telescopeFramebufferL));
                    this.alphaShaders.put(((RenderTargetExtension)this.telescopeFramebufferR).getName(), this.createShaderGroup(resourcelocation, this.telescopeFramebufferR));
                    this.alphaShaders.put(((RenderTargetExtension)this.cameraRenderFramebuffer).getName(), this.createShaderGroup(resourcelocation, this.cameraRenderFramebuffer));
                }

                for (PostChain postchain1 : list1)
                {
                    postchain1.close();
                }

                minecraft.gameRenderer.checkEntityPostEffect(minecraft.getCameraEntity());
            }
            catch (Exception exception1)
            {
                System.out.println(exception1.getMessage());
                System.exit(-1);
            }

            if (minecraft.screen != null)
            {
                int l2 = minecraft.getWindow().getGuiScaledWidth();
                int j3 = minecraft.getWindow().getGuiScaledHeight();
                minecraft.screen.init(minecraft, l2, j3);
            }

            long i3 = (long)(minecraft.getWindow().getScreenWidth() * minecraft.getWindow().getScreenHeight());
            long k3 = (long)(i * j * 2);

            if (list.contains(RenderPass.CENTER))
            {
                k3 += i3;
            }

            if (list.contains(RenderPass.THIRD))
            {
                k3 += i3;
            }

            System.out.println("[Minecrift] New render config:\nOpenVR target width: " + eyew + ", height: " + eyeh + " [" + String.format("%.1f", (float)(eyew * eyeh) / 1000000.0F) + " MP]\nRender target width: " + i + ", height: " + j + " [Render scale: " + Math.round(dataholder.vrSettings.renderScaleFactor * 100.0F) + "%, " + String.format("%.1f", (float)(i * j) / 1000000.0F) + " MP]\nMain window width: " + minecraft.getWindow().getScreenWidth() + ", height: " + minecraft.getWindow().getScreenHeight() + " [" + String.format("%.1f", (float)i3 / 1000000.0F) + " MP]\nTotal shaded pixels per frame: " + String.format("%.1f", (float)k3 / 1000000.0F) + " MP (eye stencil not accounted for)");
            this.lastDisplayFBWidth = i;
            this.lastDisplayFBHeight = j;
            this.reinitFramebuffers = false;
        }
    }

    public boolean wasDisplayResized()
    {
        Minecraft minecraft = Minecraft.getInstance();
        int i = minecraft.getWindow().getScreenHeight();
        int j = minecraft.getWindow().getScreenWidth();
        boolean flag = this.dispLastHeight != i || this.dispLastWidth != j;
        this.dispLastHeight = i;
        this.dispLastWidth = j;
        return flag;
    }
}
