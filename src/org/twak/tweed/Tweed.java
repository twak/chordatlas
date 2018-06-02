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
import javax.vecmath.Point3d;
import javax.vecmath.Tuple3d;
import javax.vecmath.Vector3d;

import org.apache.commons.io.FileUtils;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeocentricCRS;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.twak.siteplan.jme.Jme3z;
import org.twak.tweed.gen.FeatureCache;
import org.twak.tweed.gen.GISGen;
import org.twak.tweed.handles.Handle;
import org.twak.tweed.handles.HandleMe;
import org.twak.tweed.tools.FacadeTool;
import org.twak.tweed.tools.HouseTool;
import org.twak.tweed.tools.MoveTool;
import org.twak.tweed.tools.PlaneTool;
import org.twak.tweed.tools.SelectTool;
import org.twak.tweed.tools.TextureTool;
import org.twak.tweed.tools.Tool;
import org.twak.utils.Mathz;
import org.twak.utils.ui.ListDownLayout;
import org.twak.utils.ui.WindowManager;

import com.google.common.io.Files;
import com.google.common.io.LineProcessor;
import com.jme3.app.DebugKeysAppState;
import com.jme3.app.FlyCamAppState;
import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetKey;
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
import com.jme3.post.filters.ColorOverlayFilter;
import com.jme3.post.filters.FXAAFilter;
import com.jme3.post.ssao.SSAOFilter;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.ui.Picture;

public class Tweed extends SimpleApplication {
	
	public static final String LAT_LONG = "EPSG:4326";
	
	public final static String 
			CLICK = "Click", 
			MOUSE_MOVE = "MouseyMousey", 
			SPEED_UP = "SpeedUp", SPEED_DOWN = "SpeedDown",
			AMBIENT_UP = "AmbientUp", AMBIENT_DOWN = "AmbientDown", 
			TOGGLE_ORTHO = "ToggleOrtho", 
			FOV_UP ="FovUp", FOV_DOWN = "FovDown";

	public TweedFrame frame;
	public FeatureCache features;
	private Picture background;
	public Vector3d cursorPosition;


	Tool[] tools = new Tool[] {  
			new SelectTool(this), 
			new HouseTool(this),
			new MoveTool(this), 
//			new AlignTool(this), 
			new FacadeTool(this),
//			new PlaneTool(this) 
			new TextureTool(this),
};
	
	public Tool tool;
	
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

//		TweedSettings.load( new File ( Tweed.DATA ) );
		
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
//		setAmbient( 0 );


		setDisplayFps(false);
		setDisplayStatView(false);
		
		clearBackground();
		buildBackground();
		
		getFlyByCamera().setDragToRotate(true);

		setTool(SelectTool.class);

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
		
		TweedSettings.loadDefault();
		
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
		
		double[] lastOffset = new double[] { Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY };
		
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
		
		if (guessCRS == null || lastOffset[0] == Double.POSITIVE_INFINITY) {
			JOptionPane.showMessageDialog( frame.frame, "Failed to guess coordinate system for "+gmlFile.getName() );
			return;
		}
		
//		if (TweedSettings.settings.trans != null) {
//			lastOffset[0] = TweedSettings.settings.trans[0];
//			lastOffset[1] = TweedSettings.settings.trans[1];
//		}
//		else 
			TweedSettings.settings.trans = lastOffset;
		
		TweedSettings.settings.gmlCoordSystem = guessCRS;
		
		System.out.println( "Assuming CRS " + guessCRS + " for all of " + gmlFile.getName() );
		
		MathTransform transform = CRS.findMathTransform( CRS.decode( guessCRS ), DefaultGeocentricCRS.CARTESIAN, true );
		
		System.out.println( "Using CRS --> World space offset of " + lastOffset[0] + ", " + lastOffset[1] );
		
		TweedSettings.settings.toOrigin = buildOrigin ( lastOffset[0], lastOffset[1], transform );
		TweedSettings.settings.fromOrigin = new Matrix4d( TweedSettings.settings.toOrigin );
		TweedSettings.settings.fromOrigin.invert();
		
		frame.addGen ( new GISGen( makeWorkspaceRelative( gmlFile ).toString(), TweedSettings.settings.toOrigin, guessCRS, this ), true );
	}

	private void setCameraPerspective() {
		
		if ( TweedSettings.settings.ortho ) {

			cam.setParallelProjection( true );
			float frustumSize =  TweedSettings.settings.fov*10 +100;
			float aspect = (float) cam.getWidth() / cam.getHeight();
			cam.setFrustum( -1000, 1000, -aspect * frustumSize, aspect * frustumSize, frustumSize, -frustumSize );
			
		} else {
			cam.setFrustumPerspective(  TweedSettings.settings.fov*10 +100, 16 / 9f, 0.1f, 1e3f );
			cam.setFrustumFar( 1e5f );
		}
	}

	private void setFov( int i ) {
		
		TweedSettings.settings.fov = Mathz.clamp( TweedSettings.settings.fov + i, -100, 100 );
		System.out.println("fov now " + TweedSettings.settings.fov);
		setCameraPerspective();
	}

	private void setCameraSpeed( int i ) {
		
		TweedSettings.settings.cameraSpeed = Mathz.clamp( TweedSettings.settings.cameraSpeed+i, -25, 6 );
		System.out.println("camera speed now " + TweedSettings.settings.cameraSpeed);
		getFlyByCamera().setMoveSpeed( (float) Math.pow (2, (TweedSettings.settings.cameraSpeed /2 ) + 8) );
	}
	
	private void setAmbient( int i ) {
		
		TweedSettings.settings.ambient = Mathz.clamp( TweedSettings.settings.ambient + i * 0.1, 0, 2 );
		System.out.println("ambient now " + TweedSettings.settings.ambient);
		ambient.setColor(ColorRGBA.White.mult( (float) TweedSettings.settings.ambient));
  		sun.setColor( new ColorRGBA(1f, 0.95f, 0.99f, 1f).mult( 2- (float)TweedSettings.settings.ambient) );
	}
	
	public void setTool( Class newTool ) {

		for ( Tool t : tools )
			if ( t.getClass() == newTool ) {
				setTool( t );
				return;
			}
	}
	
	public void setTool( Tool newTool ) {
		
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
	public Vector3f oldCameraLoc = new Vector3f(575.0763f, 159.23715f, -580.0377f);
	public Quaternion oldCameraRot = new Quaternion(0.029748844f, 0.9702514f, -0.16988836f, 0.16989778f);

	
	public void simpleUpdate(float tpf) {
		
//		cam.setFrustumFar( 1e4f );
		
		if (oldWidth != cam.getWidth() || oldHeight != cam.getHeight()) {
			buildBackground();
			clearBackground();
			oldWidth = cam.getWidth();
			oldHeight = cam.getHeight();
		}
		
//		System.out.println(">>" + checkForEnd);
		
		if (checkForEnd)
			if (tool.isDragging()) {
				tool.dragEnd();
			}
		
		checkForEnd = true;
		
		oldCameraLoc = cam.getLocation();
		oldCameraRot = cam.getRotation();
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
	
	private Vector3f getSurfaceSelected(float dist) {
		CollisionResult cr = getClicked();
		
		Vector3f pos = null;
		
		if (cr != null) 
			pos = cr.getContactPoint();
		
		
		if (pos == null) {
			Vector3f dir = cam.getWorldCoordinates( getInputManager().getCursorPosition(), -dist );
			dir.subtractLocal( cam.getLocation() );
			new Ray( cam.getLocation(), dir ).intersectsWherePlane( new Plane(Jme3z.UP, 0), pos = new Vector3f() );
		}
		return pos;
	}
	
	private AnalogListener analogListener = new AnalogListener() {
		public void onAnalog( String name, float intensity, float tpf ) {
			
			if ( name.equals( CLICK ) ) {

				if ( tool.isDragging() ) {

					tool.dragging( inputManager.getCursorPosition(), getSurfaceSelected( 0 ) );
					checkForEnd = false;
				} else {
					CollisionResult cr = getClicked();

					if ( cr == null )
						tool.clear();

					if ( tool instanceof PlaneTool || (cr != null && cr.getGeometry().getUserData( CLICK ) instanceof Handle ) ) {
						tool.dragStart( cr == null ? null : cr.getGeometry(), inputManager.getCursorPosition(), getSurfaceSelected( 0 ) );
						checkForEnd = false;
					} else {

						if ( cr != null ) {

							Spatial target = cr.getGeometry();

							while ( target.getParent().getUserData( HandleMe.class.getSimpleName() ) != null )
								target = target.getParent();

							tool.clickedOn( target, cr.getContactPoint(), inputManager.getCursorPosition() );
						}
					}

				}
			}
		}
	};

	public void clearBackground() {

		if ( !TweedSettings.settings.SSAO ) {

			if ( background == null ) {
				background = new Picture( "background" );
			}
			Material mat1 = new Material( getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md" );
			mat1.setColor( "Color", ColorRGBA.Black );
			background.setMaterial( null );
			background.setMaterial( mat1 );
		}
	}

	final static String BG_LOC = "scratch/bg.jpg";

	public void setBackground( BufferedImage bi ) {
		if ( !TweedSettings.settings.SSAO ) {

			try {

				long hack = System.currentTimeMillis();

				ImageIO.write( bi, "jpg", new File( JME + BG_LOC + hack + ".jpg" ) );
				background.setMaterial( null );
				getAssetManager().deleteFromCache( new AssetKey<>( BG_LOC ) );

				Material mat = new Material( getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md" );
				mat.setTexture( "ColorMap", getAssetManager().loadTexture( BG_LOC + hack + ".jpg" ) );
				//					mat.setColor("Color", ColorRGBA.Red); 
				background.setMaterial( mat );

				//					background.setImage(assetManager, BG_LOC, false);
				background.updateGeometricState();

				gainFocus();

			} catch ( IOException e ) {
				e.printStackTrace();
			}
		}
	}

	protected void buildBackground() {

		if ( !TweedSettings.settings.SSAO ) {
			String bgKey = "background";

			//		clearBackground();

			background.setWidth( cam.getWidth() );
			background.setHeight( cam.getHeight() );
			background.setPosition( 0, 0 );
			background.updateGeometricState();

			ViewPort pv = renderManager.createPreView( bgKey, cam );
			pv.setClearFlags( true, true, true );
			pv.attachScene( background );

			viewPort.setClearFlags( false, true, true );

			background.updateGeometricState();
		}

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

		panel.add( new JLabel("tools:") );
		
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
	

	public static String DATA; // we read the datafiles from here
	public static String SCRATCH; // we read the datafiles from here
	public static String JME; // root of asset resource-tree for jMonkey
	
	public void initFrom( String dataDir ) {

		if (JME != null) {
			
			assetManager.unregisterLocator( JME, FileLocator.class );
			deleteScratch();
		}
		
		DATA = dataDir; //    =   System.getProperty("user.home")+"/data/regent"
		SCRATCH = DATA + File.separator + "scratch" + File.separator;
		
		new File (SCRATCH).mkdirs();
		
		JME = DATA + File.separator;

		cam.setLocation( TweedSettings.settings.cameraLocation );
		cam.setRotation( TweedSettings.settings.cameraOrientation );


		assetManager.registerLocator(Tweed.JME, FileLocator.class);
		
		features = new FeatureCache( new File ( dataDir, FeatureCache.FEATURE_FOLDER ), this );
		
		setFov( 0 );
		setCameraSpeed( 0 );
		setAmbient( 0 );

		frame.setGens( TweedSettings.settings.genList );
		
		WindowManager.setTitle( TweedFrame.APP_NAME +" " + new File( dataDir ).getName() );
	}

	public static void deleteScratch() {
		if (Tweed.SCRATCH.contains ("scratch"))
			try {
				FileUtils.deleteDirectory( new File (Tweed.SCRATCH) );
			} catch ( IOException e ) {
				e.printStackTrace();
			}
	}

	public File makeWorkspaceRelative( File f ) {
		return new File( DATA ).toPath().relativize( f.toPath() ).toFile();
	}

	public static File toWorkspace( String f ) { 
		
		return toWorkspace( new File (f) );
	}
	
	public static File toWorkspace( File f ) {

		if (DATA == null)
			return f;
		
		if ( !f.isAbsolute() ) {
			f = new File( new File( DATA ), f.toString() );
		}

		return f;
	}

	public double[] worldToLatLong( Tuple3d to3 ) {
		
		double[] trans = new double[] { to3.x, 0, to3.z };
		
		try {
		// two part transform to align heights - geoid for 4326 is different to 27700
		
		if ( TweedSettings.settings.gmlCoordSystem.equals( "EPSG:2062" ) ) { // oviedo :(
			System.out.println( "******* dirty hack in place for CS" );

			trans[ 2 ] += 258;
			trans[ 0 ] -= 3;
			trans[ 0 ] -= 3;
		}
		
		{
			Point3d tmp = new Point3d( trans );
			TweedSettings.settings.fromOrigin.transform( tmp );
			tmp.get( trans );
		}
		
			
		MathTransform cartesian2Country = CRS.findMathTransform( 
				DefaultGeocentricCRS.CARTESIAN, 
				CRS.decode( TweedSettings.settings.gmlCoordSystem ), 
				true );
		
		cartesian2Country.transform( trans, 0, trans, 0, 1 );
		
		if ( TweedSettings.settings.gmlCoordSystem.equals( "EPSG:3042" ) ) { /* madrid?! */
			System.out.println( "******* dirty hack in place for flipped CS" );
			double tmp = trans[ 0 ];
			trans[ 0 ] = trans[ 1 ];
			trans[ 1 ] = tmp;
		}
		
		MathTransform country2latlong;
		
			country2latlong = CRS.findMathTransform( 
					CRS.decode( TweedSettings.settings.gmlCoordSystem ),
					CRS.decode( Tweed.LAT_LONG ), 
					true );
		
			country2latlong.transform( trans, 0, trans, 0, 1 );


			return new double[] {trans[0], trans[1]};
			
		} catch ( Throwable e ) {
			e.printStackTrace();
		}
		
		return new double[] {Double.NaN, Double.NaN };
	}
}
