package org.twak.tweed;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.vecmath.Matrix4d;
import javax.vecmath.Vector3d;

import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeocentricCRS;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.twak.siteplan.jme.Jme3z;
import org.twak.tweed.gen.FeatureGen;
import org.twak.tweed.gen.GISGen;
import org.twak.tweed.gen.HeightGen;
import org.twak.tweed.gen.MiniGen;
import org.twak.tweed.gen.PanoGen;
import org.twak.tweed.gen.ResultsGen;
import org.twak.tweed.handles.Handle;
import org.twak.tweed.handles.HandleMe;
import org.twak.tweed.tools.AlignTool;
import org.twak.tweed.tools.FacadeTool;
import org.twak.tweed.tools.HandleTool;
import org.twak.tweed.tools.HouseTool;
import org.twak.tweed.tools.SelectTool;
import org.twak.tweed.tools.Tool;
import org.twak.utils.Mathz;
import org.twak.utils.MutableDouble;
import org.twak.utils.ui.ListDownLayout;

import com.google.common.io.Files;
import com.google.common.io.LineProcessor;
import com.jme3.app.DebugKeysAppState;
import com.jme3.app.FlyCamAppState;
import com.jme3.app.SimpleApplication;
import com.jme3.asset.TextureKey;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.audio.AudioListenerState;
import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.light.PointLight;
import com.jme3.material.Material;
import com.jme3.material.TechniqueDef.LightMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Plane;
import com.jme3.math.Quaternion;
import com.jme3.math.Ray;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.FXAAFilter;
import com.jme3.post.ssao.SSAOFilter;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.ui.Picture;

public class Tweed extends SimpleApplication {

	
	public final static String DATA   =   System.getProperty("user.home")+"/data/regent"; // we read the datafiles from here
	public final static String SCRATCH   =   DATA + File.separator + "scratch" + File.separator; // we read the datafiles from here
	public final static String JME    = DATA+File.separator; // root of asset resource-tree for jMonkey
	
	public static final String LAT_LONG = "EPSG:4326";
	
	public final static String 
			CLICK = "Click", 
			MOUSE_MOVE = "MouseyMousey", 
			SPEED_UP = "SpeedUp", SPEED_DOWN = "SpeedDown",
			AMBIENT_UP = "AmbientUp", AMBIENT_DOWN = "AmbientDown", 
			TOGGLE_ORTHO = "ToggleOrtho", 
			FOV_UP ="FovUp", FOV_DOWN = "FovDown";

	public TweedFrame frame;
	
	public HeightGen heights;
	public FeatureGen features;
	private Picture background;
	
	public Vector3d cursorPosition;
	protected String lastCRS;
	public double[] lastOffset;


	Tool[] tools = new Tool[] {  
			new SelectTool(this), 
			new HouseTool(this), 
			new HandleTool(this), 
			new AlignTool(this), 
			new FacadeTool(this) };
	
	public Tool tool;
	
	public Matrix4d toOrigin, fromOrigin;
	
	private AmbientLight ambient;
	private DirectionalLight sun;
	private PointLight point;
	
	public Node debug;
	
	public Tweed(TweedFrame frame) {
		super( new FlyCamAppState(), new AudioListenerState(), new DebugKeysAppState());
		this.frame = frame;
	}
	
	@Override
	public void reshape( int w, int h ) {
		super.reshape( w, h );
	}
	
	public void simpleInitApp() {

		TweedSettings.load( new File ( Tweed.DATA ) );
		
		point = new PointLight();
		point.setEnabled( true );
		point.setColor( ColorRGBA.White.mult(4) );
		point.setRadius( 50 );
		rootNode.addLight( point );
		
		sun = new DirectionalLight();
//		sun.setDirection(new Vector3f(-0.0f, -1f, -0f).normalizeLocal());
		sun.setDirection(new Vector3f(-0.1f, -0.7f, -1.0f).normalizeLocal());
		sun.setColor(new ColorRGBA(1f, 0.95f, 0.99f, 1f));
		rootNode.addLight(sun);

	    renderManager.setPreferredLightMode(LightMode.SinglePass); // enable multiple lights
	    renderManager.setSinglePassLightBatchSize(16);
		
		ambient = new AmbientLight();
		rootNode.addLight(ambient);
		setAmbient( 0 );

		assetManager.registerLocator(Tweed.JME, FileLocator.class);

		setDisplayFps(false);
		setDisplayStatView(false);
		
		buildBackground();
		
		getFlyByCamera().setDragToRotate(true);

		setTool(tools[0]);

		debug = new Node( "dbg" );
		rootNode.attachChild( debug );
		
//		String folder = ; // data-source
		
//	    SpotLightShadowRenderer shadows = new SpotLightShadowRenderer(assetManager, 1024);
//	    shadows.setLight(sun);
//	    shadows.setShadowIntensity(0.3f);
//	    shadows.setEdgeFilteringMode(EdgeFilteringMode.PCF8);
//	    viewPort.addProcessor(shadows);
		
		cam.setLocation(TweedSettings.settings.cameraLocation);
		cam.setRotation(TweedSettings.settings.cameraOrientation);
	    
	
		setFov(0);
		setCameraSpeed( 0 );
		
		if ( TweedSettings.settings.SSAO ) {
			
			FilterPostProcessor fpp = new FilterPostProcessor( assetManager );
			SSAOFilter filter = new SSAOFilter( 0.50997847f, 1.440001f, 1.39999998f, 0 );
//			fpp.addFilter( new ColorOverlayFilter( ColorRGBA.Magenta ));
			fpp.addFilter( filter );
			fpp.addFilter( new FXAAFilter() );
			viewPort.addProcessor( fpp );
		}
		
		try {
			
			addGML(new File (Tweed.DATA + "/gis.gml"), TweedSettings.settings.gmlCoordSystem );
			
			frame.addGen ( new MiniGen   ( new File ( Tweed.DATA +"/minimesh/"   ), this ), true );
			frame.addGen ( new PanoGen   ( new File ( Tweed.DATA +"/panos/"      ), this, LAT_LONG ), true );
			frame.addGen ( new FeatureGen( new File ( Tweed.DATA+"/features/"), this ), false );
			frame.addGen ( new ResultsGen( new File ( Tweed.DATA+"/solutions/"), this ), true );

		} catch ( Throwable e ) {
			e.printStackTrace();
		}
		
		inputManager.addMapping(MOUSE_MOVE, new MouseAxisTrigger( MouseInput.AXIS_X, false ) );
		inputManager.addMapping(MOUSE_MOVE, new MouseAxisTrigger( MouseInput.AXIS_Y, false ) );
		inputManager.addMapping(MOUSE_MOVE, new MouseAxisTrigger( MouseInput.AXIS_X, true ) );
		inputManager.addMapping(MOUSE_MOVE, new MouseAxisTrigger( MouseInput.AXIS_Y, true ) );
		inputManager.addListener( moveListener, MOUSE_MOVE );
		
		inputManager.addMapping(CLICK, new MouseButtonTrigger(MouseInput.BUTTON_RIGHT));
		inputManager.addListener(analogListener, CLICK);
		
		inputManager.addMapping( SPEED_UP, new KeyTrigger( KeyInput.KEY_UP ) );
		inputManager.addMapping( SPEED_DOWN, new KeyTrigger( KeyInput.KEY_DOWN ) );
		
		inputManager.addMapping( AMBIENT_UP, new KeyTrigger( KeyInput.KEY_RIGHT ) );
		inputManager.addMapping( AMBIENT_DOWN, new KeyTrigger( KeyInput.KEY_LEFT ) );
		
		inputManager.addMapping( FOV_UP, new KeyTrigger( KeyInput.KEY_PGUP ) );
		inputManager.addMapping( FOV_DOWN, new KeyTrigger( KeyInput.KEY_PGDN ) );
		
		inputManager.addMapping( TOGGLE_ORTHO, new KeyTrigger( KeyInput.KEY_O ) );
		
		inputManager.addListener( new com.jme3.input.controls.ActionListener() {
			
			@Override
			public void onAction( String name, boolean isPressed, float tpf ) {
				if (name == SPEED_UP)
					setCameraSpeed(+1);
				else
					setCameraSpeed(-1);
				
			}
		}, SPEED_UP, SPEED_DOWN );
		
		inputManager.addListener( new com.jme3.input.controls.ActionListener() {
			
			@Override
			public void onAction( String name, boolean isPressed, float tpf ) {
				
				if (!isPressed)
					return;
				
				if (name == AMBIENT_UP)
					setAmbient(+1);
				else
					setAmbient(-1);
			}
		}, AMBIENT_UP, AMBIENT_DOWN );
		
		inputManager.addListener( new com.jme3.input.controls.ActionListener() {
			
			@Override
			public void onAction( String name, boolean isPressed, float tpf ) {
				
				if (!isPressed)
					return;
				
				if (name == FOV_UP)
					setFov(+1);
				else
					setFov(-1);
			}
		}, FOV_UP, FOV_DOWN );
		
		inputManager.addListener( new com.jme3.input.controls.ActionListener() {
			
			@Override
			public void onAction( String name, boolean isPressed, float tpf ) {
				if ( isPressed ) {
					TweedSettings.settings.ortho = !TweedSettings.settings.ortho;
					setCameraPerspective();
				}
			}
		}, TOGGLE_ORTHO );
		
	}
	
	private final static Pattern SRS_EX    = Pattern.compile( ".*srsName=\\\"([^\\\"]*).*" ),
								 OFFSET_EX = Pattern.compile(".*<gml:X>([0-9\\\\.]*)</gml:X><gml:Y>([0-9\\\\.]*).*");
	
	public void addGML( File gmlFile, String guessCRS) throws Exception {
		
		lastOffset = new double[] { Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY };
		
		if (guessCRS == null)
			guessCRS = Files.readLines( gmlFile, Charset.forName( "UTF-8" ), new LineProcessor<String>() {

			String crs;
			
			@Override
			public boolean processLine( String line ) throws IOException {
				
				Matcher m = SRS_EX.matcher( line );
				
				if (m.matches()) {
					crs = m.group( 1 );
					return false;
				}
				
				m = OFFSET_EX.matcher( line );
				
				if (m.matches() && lastOffset[0] == Double.POSITIVE_INFINITY ) {// bounds def before we see a CRS...
					lastOffset[0] = Double.parseDouble( m.group( 1 ) );
					lastOffset[1] = Double.parseDouble( m.group( 2 ) );
				}
				
				return true;
			}

			@Override
			public String getResult() {
				return crs;
			}
		} );
		
		if (guessCRS == null) {
			JOptionPane.showMessageDialog( frame.frame, "Failed to guess coordinate system for "+gmlFile.getName() );
			return;
		}
		

		lastCRS = guessCRS;
		
		System.out.println( "Assuming CRS " + guessCRS + " for all of " + gmlFile.getName() );
		
		MathTransform transform = CRS.findMathTransform( CRS.decode( guessCRS ), DefaultGeocentricCRS.CARTESIAN, true );
		
		if (lastOffset[0] == Double.POSITIVE_INFINITY ) {
			lastOffset[0] = TweedSettings.settings.trans[0];
			lastOffset[1] = TweedSettings.settings.trans[1];
		}
		
		System.out.println( "Using CRS --> World space offset of " + lastOffset[0] + ", " + lastOffset[1] );
		
		toOrigin = buildOrigin ( lastOffset[0], lastOffset[1], transform );
		fromOrigin = new Matrix4d( toOrigin );
		fromOrigin.invert();
		
		frame.addGen ( new GISGen( gmlFile.toString(), toOrigin, guessCRS, this ), true );
	}

	private void setCameraPerspective() {
		
		if ( TweedSettings.settings.ortho ) {

			cam.setParallelProjection( true );
			float frustumSize =  TweedSettings.settings.fov*10 +100;
			float aspect = (float) cam.getWidth() / cam.getHeight();
			cam.setFrustum( -1000, 1000, -aspect * frustumSize, aspect * frustumSize, frustumSize, -frustumSize );
			
		} else {
			cam.setFrustumPerspective(  TweedSettings.settings.fov*10 +100, 16 / 9f, 0.1f, 1e3f );
			cam.setFrustumFar( 1e4f );
		}
	}

	private void setFov( int i ) {
		
		TweedSettings.settings.fov = Mathz.clamp( TweedSettings.settings.fov + i, -100, 100 );
		System.out.println("fov now " + TweedSettings.settings.fov);
		setCameraPerspective();
	}

	private void setCameraSpeed( int i ) {
		
		TweedSettings.settings.cameraSpeed = Mathz.clamp( TweedSettings.settings.cameraSpeed+i, -25, 3 );
		System.out.println("camera speed now " + TweedSettings.settings.cameraSpeed);
		getFlyByCamera().setMoveSpeed( (float) Math.pow (2, (TweedSettings.settings.cameraSpeed /2 ) + 8) );
	}
	
	private void setAmbient( int i ) {
		
		TweedSettings.settings.ambient = Mathz.clamp( TweedSettings.settings.ambient + i * 0.1, 0, 2 );
		System.out.println("ambient now " + TweedSettings.settings.ambient);
		ambient.setColor(ColorRGBA.White.mult( (float) TweedSettings.settings.ambient));
  		sun.setColor( new ColorRGBA(1f, 0.95f, 0.99f, 1f).mult( 2- (float)TweedSettings.settings.ambient) );
	}
	
	private void setTool( Tool newTool ) {
		
		Tool oldTool = tool;
		
		enqueue( new Runnable() {
			@Override
			public void run() {
				if (oldTool != null)
					oldTool.deactivate();
				
				newTool.activate( Tweed.this );;
			}
		});
		
		this.tool = newTool;
		
		frame.genUI.removeAll();
		this.tool.getUI(frame.genUI);
		frame.genUI.revalidate();
		frame.genUI.doLayout();
		frame.genUI.repaint();
		
		gainFocus();
	}

	/**
	 * Transform toCartesian (around x,y) to a renderable coordinate system with y-up.
	 */
	private Matrix4d buildOrigin( double x, double y, MathTransform toCartesian ) throws TransformException {
		
		double delta = 1e-6;
		
		double[] frame = new double[] {
				x      , y, 
				x+delta, y, 
				x      , y+delta, 
				0      , 0      ,0 };
		
		toCartesian.transform( frame, 0, frame, 0, 3 );
		
		Vector3d o = new Vector3d(frame[0], frame[1], frame[2]),
				 a = new Vector3d(frame[3], frame[4], frame[5]),
				 b = new Vector3d(frame[6], frame[7], frame[8]),
				 c = new Vector3d();
		
		a.sub( o );
		b.sub( o );
				
		a.normalize();
		b.normalize();
		
		c.cross( a, b );
		
		Matrix4d out = new Matrix4d();
		
		out.setRow( 0, -a.x, -a.y, -a.z, 0 );
		out.setRow( 1, c.x, c.y, c.z, 0 );
		out.setRow( 2, b.x, b.y, b.z, 0 );
		out.setRow( 3, 0, 0, 0, 1 );
		
		out.transform( o );
		
		out.m03 = -o.x;
		out.m13 = -o.y;
		out.m23 = -o.z;
		
		return out;
	}
	
	boolean checkForEnd = true;
	int oldWidth, oldHeight;
	
	public void simpleUpdate(float tpf) {
		
//		cam.setFrustumFar( 1e4f );
		
		if (oldWidth != cam.getWidth() || oldHeight != cam.getHeight()) {
			buildBackground();
			oldWidth = cam.getWidth();
			oldHeight = cam.getHeight();
		}
		
		if (checkForEnd)
			if (tool.isDragging()) {
				tool.dragEnd();
			}
		
		checkForEnd = true;
		
		TweedSettings.settings.cameraLocation = cam.getLocation();
		TweedSettings.settings.cameraOrientation = cam.getRotation();
	}
	
	
	
	private AnalogListener moveListener = new AnalogListener() {
		@Override
		public void onAnalog( String name, float value, float tpf ) {
			CollisionResult cr = getClicked();

			Vector3f pos = null;
			
			if (cr != null) 
				pos = cr.getContactPoint();
	
			
			if (pos == null) {
				Vector3f dir = cam.getWorldCoordinates( getInputManager().getCursorPosition(), -10 );
				dir.subtractLocal( cam.getLocation() );
				new Ray( cam.getLocation(), dir ).intersectsWherePlane( new Plane(Jme3z.UP, 0), pos = new Vector3f() );
			}
			
			cursorPosition = Jme3z.from( pos );
			
			if (pos != null)
				point.setPosition( pos.add ( cam.getDirection().mult( -0.3f ) ));
		}
	};
	private AnalogListener analogListener = new AnalogListener() {
		public void onAnalog(String name, float intensity, float tpf) {

			if (name.equals(CLICK)) {

				if (tool.isDragging()) {
					
					tool.dragging(inputManager.getCursorPosition());
					checkForEnd = false;
				} else {
					CollisionResult cr = getClicked();

					if (cr == null || !(cr.getGeometry().getUserData(CLICK) instanceof Handle)) {
						
						
//						if (cr != null) {
//							Object[] genA = cr.getGeometry().getUserData(Gen.class.getSimpleName());
//							if (genA != null)
//								frame.setSelected((Gen) genA[0]);
//						}

						tool.clear();

						if (cr != null) {
							
							Spatial target = cr.getGeometry();
							
							while (target.getParent().getUserData( HandleMe.class.getSimpleName() ) != null )
								target = target.getParent();
							
							tool.clickedOn(target, cr.getContactPoint(), inputManager.getCursorPosition());
						}
						
					} else {
						tool.dragStart(cr. getGeometry(), inputManager.getCursorPosition());
						checkForEnd = false;
					}
				}
			}

		}

	};
	
	public void clearBackground() {
		Material mat1 = new Material(getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
		mat1.setColor("Color", ColorRGBA.Black);
		background.setMaterial(null);
		background.setMaterial(mat1);
	}
	
	final static String BG_LOC = "Desktop/bg.png";
	public void setBackground (BufferedImage bi ) {
		
		try {
			ImageIO.write(bi, "png", new File ( JME + BG_LOC ) );
			TextureKey key = new TextureKey(BG_LOC);
			getAssetManager().deleteFromCache(key);
			background.setMaterial(null);
			
			background.setImage(assetManager, BG_LOC, false);
			background.updateGeometricState();
			
			gainFocus();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	protected void buildBackground() {
		
		String bgKey = "background";
		
//		if ( background != null ) {
//			background.removeFromParent();
//			renderManager.removePreView( bgKey );
//		}
		
		background = new Picture( "background" );

		clearBackground();
		
		background.setWidth( cam.getWidth() );
		background.setHeight( cam.getHeight() );
		background.setPosition( 0, 0 );
		background.updateGeometricState();

		ViewPort pv = renderManager.createPreView( bgKey, cam );
		pv.setClearFlags( true, true, true );
		pv.attachScene( background );
//		viewPort.setClearFlags( false, true, true );
		background.updateGeometricState();

	}

	private CollisionResult getClicked() {
		
		CollisionResults results = new CollisionResults();
		Vector2f click2d = inputManager.getCursorPosition();
		Vector3f click3d = cam.getWorldCoordinates(
		    new Vector2f(click2d.x, click2d.y), 0f).clone();
		Vector3f dir = cam.getWorldCoordinates(
		    new Vector2f(click2d.x, click2d.y), 1f).subtractLocal(click3d).normalizeLocal();
		Ray ray = new Ray(click3d, dir);
		
		rootNode.collideWith(ray, results);
		
		if (results.size() > 0) 
			return results.getClosestCollision();
		else
			return null;
	}

	public void clearCurrentToolState() {
		tool.clear();
	}

	public void addUI( JPanel panel ) {

		panel.setLayout( new ListDownLayout() );
		ButtonGroup bg = new ButtonGroup();

		panel.add( new JLabel("tools") );
		
		for ( Tool t : tools ) {

			JToggleButton tb = new JToggleButton( t.getName() );
			bg.add( tb );

			tb.addActionListener( new ActionListener() {

				@Override
				public void actionPerformed( ActionEvent e ) {
					Tweed.this.setTool( t );
				}
			} );

			panel.add( tb );
		}
		
		((JToggleButton)panel.getComponent( 1 ) ).setSelected( true );

	}

	public void resetCamera() {
		cam.setLocation( new Vector3f() );
		cam.setRotation( new Quaternion() );
		setCameraSpeed( 0 );
		TweedSettings.settings.ortho = false;
		TweedSettings.settings.fov = 0;
		setCameraPerspective();
	}

	public Component frame() {
		return frame.frame;
	}
}
