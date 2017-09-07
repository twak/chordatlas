package org.twak.tweed;

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.vecmath.Vector3d;

import org.twak.tweed.gen.FeatureGen;
import org.twak.tweed.gen.GISGen;
import org.twak.tweed.gen.Gen;
import org.twak.tweed.gen.MiniGen;
import org.twak.tweed.gen.ObjGen;
import org.twak.tweed.gen.PanoGen;
import org.twak.utils.PaintThing;
import org.twak.utils.WeakListener;
import org.twak.utils.geom.HalfMesh2;
import org.twak.utils.geom.ObjDump;
import org.twak.utils.ui.ListDownLayout;
import org.twak.utils.ui.ListRightLayout;
import org.twak.utils.ui.SimpleFileChooser;
import org.twak.utils.ui.SimplePopup2;
import org.twak.utils.ui.WindowManager;
import org.twak.viewTrace.SuperMeshPainter;

import com.jme3.scene.Node;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeCanvasContext;
import com.thoughtworks.xstream.XStream;

public class TweedFrame {

	public Tweed tweed;
	Canvas canvas;
	public JFrame frame;

	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool( 1 );

	public static boolean HEADLESS = false;
	public static TweedFrame instance;

	public TweedFrame() {

		instance = this;

		frame = new JFrame();

		WindowManager.register( frame );

		Dimension d3Dim = new Dimension( 1024, 640 );

		AppSettings settings = new AppSettings( true );

		settings.setWidth( d3Dim.width );
		settings.setHeight( d3Dim.height );
		settings.setSamples( 4 );
		settings.setVSync( true );
		settings.setFrameRate( 60 );

		tweed = new Tweed( this );
		tweed.setSettings( settings );
		tweed.createCanvas();
		JmeCanvasContext ctx = (JmeCanvasContext) tweed.getContext();
		ctx.setSystemListener( tweed );

		canvas = ctx.getCanvas();
		canvas.setPreferredSize( d3Dim );

		frame.setLayout( new BorderLayout() );
		frame.add( buildUI(), BorderLayout.EAST );
		frame.add( canvas, BorderLayout.CENTER );
		frame.setExtendedState( frame.getExtendedState() | JFrame.MAXIMIZED_BOTH );

		frame.addWindowListener( new WindowAdapter() {
			public void windowClosing( WindowEvent e ) {
				TweedSettings.save(true);
			};
		} );

		scheduler.scheduleAtFixedRate( new Runnable() {
			@Override
			public void run() {
				TweedSettings.save(true);
			}
		}, 30, 30, TimeUnit.SECONDS );

		scheduler.scheduleAtFixedRate( new Runnable() {
			@Override
			public void run() {
				Vector3d pt = tweed.cursorPosition;
				if ( coordLabel != null ) {
					worldLabel.setText( pt == null ? "..." : String.format( "%.4f, %.4f ", 
							pt.x, 
							pt.z) );
					coordLabel.setText( pt == null ? "..." : String.format( "%.4f, %.4f ", 
							pt.x + tweed.lastOffset[0], 
							pt.z + tweed.lastOffset[1]) );
					crsLabel.setText(tweed.lastCRS);
				}

				JFrame.setDefaultLookAndFeelDecorated( true );

			}
		}, 100, 100, TimeUnit.MILLISECONDS );

		frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
		frame.pack();
		frame.setVisible( !HEADLESS );

		tweed.startCanvas();
	}

	public List<Gen> genList = new ArrayList<Gen>();
	JPanel genUI = new JPanel();
	JPanel layerList;

	public JComponent buildUI() {

		JPanel out = new JPanel( new BorderLayout() );

		JMenuBar menuBar = new JMenuBar();
		frame.setJMenuBar( menuBar );

		//Build the first menu.
		JMenu menu = new JMenu( "File" );
		menuBar.add( menu );

		JMenuItem save = new JMenuItem( "save", KeyEvent.VK_S );
		save.setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_S, ActionEvent.CTRL_MASK ) );
		menu.add(save);

		save.addActionListener( new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed( ActionEvent e ) {
				if (TweedSettings.folder == null) {
					new SimpleFileChooser(frame) {
						@Override
						public void heresTheFile( File f ) throws Throwable {
							TweedSettings.folder = f;
							TweedSettings.save(false);
						}
					};
				}
				else
					TweedSettings.save(false);
			}
		} );

		JMenuItem load = new JMenuItem( "load...", KeyEvent.VK_S );
		load.setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_O, ActionEvent.CTRL_MASK ) );
		menu.add(load);
		load.addActionListener( new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed( ActionEvent e ) {
					new SimpleFileChooser(frame, false, "select a workspace", new File ( Tweed.DATA ), "tweed.xml") {
						
						@Override
						public void heresTheFile( File f ) throws Throwable {
							TweedSettings.load(f);
							setGens( TweedSettings.settings.genList );
						}
					};
			}
		} );

		JMenuItem neu = new JMenuItem( "new", KeyEvent.VK_S );
		neu.setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_S, ActionEvent.CTRL_MASK ) );
		menu.add(neu);
		
		neu.addActionListener( new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed( ActionEvent e ) {
				TweedSettings.folder = null;
			}
		} );
		
		JMenuItem remove = new JMenuItem( "delete layer", KeyEvent.VK_MINUS );
		remove.setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_MINUS, ActionEvent.CTRL_MASK ) );
		menu.add( remove );
		remove.addActionListener( new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed( ActionEvent e ) {
				if ( selectedGen != null )
					removeGen( selectedGen );
			};
		} );

		JMenuItem resetBG = new JMenuItem( "reset background", KeyEvent.VK_MINUS );
		resetBG.setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_MINUS, ActionEvent.CTRL_MASK ) );
		menu.add( resetBG );
		resetBG.addActionListener( new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed( ActionEvent e ) {
				tweed.enqueue( new Runnable() {
					@Override
					public void run() {
						TweedFrame.this.tweed.clearBackground();
					}
				} );
			};
		} );

		JMenuItem obj = new JMenuItem( "export obj", KeyEvent.VK_O );
		obj.setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_0, ActionEvent.CTRL_MASK ) );
		menu.add( obj );
		obj.addActionListener( new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed( ActionEvent e ) {

				ObjDump dump = new ObjDump();

				for ( Gen g : genList )
					if ( g.visible && g instanceof IDumpObjs )
						( (IDumpObjs) g ).dumpObj( dump );

				new SimpleFileChooser(frame, true, "save all as obj", new File (Tweed.SCRATCH, "all.obj"), "obj") {
					@Override
					public void heresTheFile( File f ) throws Throwable {
						dump.dump( f );
					}
				}; 
				

			};
		} );

		JMenuItem resetCam = new JMenuItem( "reset view", KeyEvent.VK_R );
		resetCam.setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_R, ActionEvent.CTRL_MASK ) );
		menu.add( resetCam );
		resetCam.addActionListener( new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed( ActionEvent e ) {
				tweed.resetCamera();
			};
		} );

		layerList = new JPanel( new ListDownLayout() );

		JPanel layers = new JPanel();
		layers.setLayout( new BorderLayout() );
		layers.add( new JLabel( "layers" ), BorderLayout.NORTH );

		JScrollPane listScroll = new JScrollPane( layerList );
		listScroll.getVerticalScrollBar().setUnitIncrement( 50 );
		listScroll.setPreferredSize( new Dimension( 200, 300 ) );
		layers.add( listScroll, BorderLayout.CENTER );

		JPanel addRemoveLayer = new JPanel();
		{
			addRemoveLayer.setLayout( new FlowLayout( FlowLayout.RIGHT ) );
			layers.add( addRemoveLayer, BorderLayout.SOUTH );

			JButton addLayer = new JButton( "+" );
			addLayer.addMouseListener( new MouseAdapter() {
				@Override
				public void mousePressed( MouseEvent e ) {
					addLayer( e );
				}
			} );
			JButton removeLayer = new JButton( "-" );
			removeLayer.addActionListener( e -> removeGen( selectedGen ) );
			addRemoveLayer.add( addLayer );
			addRemoveLayer.add( removeLayer );
		}

		JPanel options = new JPanel( new BorderLayout() );
		{
			options.add( new JLabel( "options" ), BorderLayout.NORTH );
			options.add( genUI, BorderLayout.CENTER );
		}

		JSplitPane pane = new JSplitPane( JSplitPane.VERTICAL_SPLIT, layers, options );

		out.add( pane, BorderLayout.CENTER );

		JPanel toolPanel = new JPanel( new ListRightLayout() );

		tweed.addUI( toolPanel );

		coordLabel = new JLabel( "" );
		coordLabel.setHorizontalAlignment( SwingConstants.CENTER );
		worldLabel = new JLabel( "" );
		worldLabel.setHorizontalAlignment( SwingConstants.CENTER );
		crsLabel = new JLabel( "none" );
		crsLabel.setHorizontalAlignment( SwingConstants.CENTER );

		out.add( toolPanel, BorderLayout.NORTH );
		
		JPanel coords = new JPanel( new ListDownLayout() );
		coords.add( worldLabel );
		coords.add( coordLabel );
		coords.add( crsLabel );
		
		out.add( coords, BorderLayout.SOUTH );

		out.setPreferredSize( new Dimension( 300, frame.getHeight() ) );
		
		return out;
	}

	JLabel coordLabel, crsLabel, worldLabel;

	private void addLayer( MouseEvent evt ) {

		SimplePopup2 sp = new SimplePopup2( evt );

		sp.add( "+ mesh (obj)", new Runnable() {
			@Override
			public void run() {
				new SimpleFileChooser( frame, false, "Select .obj mesh file", new File( Tweed.JME ), "obj" ) {
					public void heresTheFile( File obj ) throws Throwable {
						removeMeshSources();
						String f = new File( Tweed.JME ).toPath().relativize( obj.toPath() ).toString();
						addGen( new ObjGen( f, tweed ), true );
					};
				};
			}
		} );

		sp.add( "+ mesh (minimesh)", new Runnable() {
			@Override
			public void run() {
				new SimpleFileChooser( frame, false, "Select minimesh index file (index.xml)", new File( Tweed.JME ), "xml" ) {
					@Override
					public void heresTheFile( File f ) throws Throwable {
						removeMeshSources();
						addGen( new MiniGen( f.getParentFile(), tweed ), true );
					}
				};
			}
		} );
		
		sp.add( "+ gis (obj)", new Runnable() {
			@Override
			public void run() {
				new SimpleFileChooser( frame, false, "Select .obj gis footprints", new File( Tweed.JME ), "obj" ) {
					public void heresTheFile( File obj ) throws Throwable {
						removeGISSources();
						addGen ( new GISGen( obj, tweed ), true );
					};
				};
			}
		} );
		
		sp.add( "+ gis (gml)", new Runnable() {
			@Override
			public void run() {
				
				
				new SimpleFileChooser( frame, false, "Select .gml gis footprints", new File( Tweed.JME ), "gml" ) {
					public void heresTheFile( File gml ) throws Throwable {
						removeGISSources();
						tweed.addGML( gml, null );
					};
				};
			}
		} );
		
		sp.add( "+ panos (jpg)", new Runnable() {
			@Override
			public void run() {
				new SimpleFileChooser( frame, false, "Select one of many panoramas in a directory", new File( Tweed.JME ), "jpg" ) {
					public void heresTheFile( File oneOfMany ) throws Throwable {
						removeGens( PanoGen.class );
						addGen( new PanoGen( oneOfMany.getParentFile(), tweed, Tweed.LAT_LONG ), true );
					};
				};
			}
		} );
		
		sp.add( "+ reload features", () -> {
			removeGens( FeatureGen.class );
			tweed.frame.addGen ( new FeatureGen( new File ( Tweed.DATA+"/features/"), tweed ), false );
		} );

		
		sp.show();

	}

	private void removeMeshSources() {
		removeGens( MiniGen.class );
		removeGens( ObjGen.class );
	}
	
	private void removeGISSources() {
		removeGens( GISGen.class );
	}

	private void setGens( List<Gen> nGens ) {

		for ( Gen g : genList )
			removeGen( g );

		layerList.removeAll();
		genList.clear();

		for ( Gen g : nGens ) {
			g.gNode = new Node();
			g.tweed = tweed;
		}

		for ( Gen g : nGens ) {
			addGen( g, true );
		}
	}
	
	public void refreshGenList() {
		for ( Component c : layerList.getComponents() ) 
			if ( c instanceof GenListItem  )
				((GenListItem)c).refresh();
	}

	public void removeBelowGen( Gen below ) {

		boolean seen = false;

		List<Gen> togo = new ArrayList<>();

		for ( Gen g : genList ) {

			if ( g == below )
				seen = true;

			if ( seen )
				togo.add( g );

		}

		for ( Gen g : togo )
			removeGen( g );
	}

	public void removeGen( Gen gen ) {

		if ( gen == null ) {
			JOptionPane.showMessageDialog( frame, "no layer selected" );
			return;
		}

		tweed.enqueue( new Runnable() {
			@Override
			public void run() {
				gen.gNode.removeFromParent();
			}
		} );

		genList.remove( gen );

		Component togo = null;
		for ( Component c : layerList.getComponents() ) {
			if ( c instanceof GenListItem && ( (GenListItem) c ).gen == gen )
				togo = c;
		}

		if ( selectedGen == gen )
			selectedGen = null;

		if ( togo != null )
			layerList.remove( togo );

		layerList.revalidate();
		layerList.repaint();
	}

	public void removeGens( Class<?> klass ) {
		for ( Gen g : new ArrayList<>( genList ) )
			if ( g.getClass() == klass )
				removeGen( g );
	}

	public void addGen( Gen gen, boolean visible ) {

		gen.visible = visible;

		layerList.add( new GenListItem( gen, selectedGenListener, this, new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed( ActionEvent arg0 ) {
				setSelected( gen );
			}
		} ) );

		genList.add( gen );
		layerList.revalidate();
		layerList.repaint();

		tweed.enqueue( new Runnable() {
			@Override
			public void run() {
				try {
					gen.calculate();
				} catch ( Throwable th ) {
					th.printStackTrace();
				}

				tweed.getRootNode().updateGeometricState();
				tweed.getRootNode().updateModelBound();
				tweed.gainFocus();
			}
		} );

	}

	public Gen selectedGen;
	public WeakListener selectedGenListener = new WeakListener();

	public void setSelected( Gen gen ) {

		setGenUI( gen.getUI() );

		if ( selectedGen == gen )
			return;

		selectedGen = gen;

		selectedGenListener.fire();
	}

	public static void main( String[] args ) throws Throwable {

		WindowManager.init( "chordatlas", "/org/twak/tweed/resources/icon128.png" );

		UIManager.put( "Slider.paintValue", false );

		JPopupMenu.setDefaultLightWeightPopupEnabled( false ); // show menus over 3d canvas
		ToolTipManager.sharedInstance().setLightWeightPopupEnabled( false );

		PaintThing.lookup.put( HalfMesh2.class, new SuperMeshPainter() );

		new TweedFrame();
	}

	public void somethingChanged() {
		canvas.repaint();
	}

	public void setGenUI( JComponent ui ) {
		genUI.setLayout( new BorderLayout() );
		genUI.removeAll();
		genUI.add( ui, BorderLayout.CENTER );
		genUI.revalidate();
		genUI.doLayout();
		genUI.repaint();

	}

	public List<Gen> gens( Class<? extends Gen> klass ) {
		return genList.stream().filter( g -> g.getClass() == klass ).collect( Collectors.toList() );
	}

}
