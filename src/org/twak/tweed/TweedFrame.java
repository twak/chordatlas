package org.twak.tweed;

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
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
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.vecmath.Vector3d;

import org.twak.readTrace.MiniTransform;
import org.twak.tweed.gen.GISGen;
import org.twak.tweed.gen.Gen;
import org.twak.tweed.gen.LotInfoGen;
import org.twak.tweed.gen.MeshGen;
import org.twak.tweed.gen.MiniGen;
import org.twak.tweed.gen.ObjGen;
import org.twak.tweed.gen.PanoGen;
import org.twak.tweed.gen.skel.SkelGen;
import org.twak.utils.PaintThing;
import org.twak.utils.WeakListener;
import org.twak.utils.geom.HalfMesh2;
import org.twak.utils.geom.ObjDump;
import org.twak.utils.ui.JLazyMenu;
import org.twak.utils.ui.ListDownLayout;
import org.twak.utils.ui.ListRightLayout;
import org.twak.utils.ui.SimpleFileChooser;
import org.twak.utils.ui.SimplePopup2;
import org.twak.utils.ui.WindowManager;
import org.twak.utils.ui.auto.Auto;
import org.twak.viewTrace.SuperMeshPainter;

import com.jme3.scene.Node;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeCanvasContext;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;

public class TweedFrame {

	private static final String TWEED_XML = "tweed.xml";
	public static final String APP_NAME = "chordatlas";
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

		canvas.addComponentListener( new ComponentAdapter() {
			@Override
			public void componentResized( ComponentEvent arg0 ) {
				tweed.setCameraPerspective();
			}
		} );
		
		frame.setLayout( new BorderLayout() );
		frame.add( buildUI(), BorderLayout.EAST );
		frame.add( canvas, BorderLayout.CENTER );
		frame.setExtendedState( frame.getExtendedState() | JFrame.MAXIMIZED_BOTH );

		frame.addWindowListener( new WindowAdapter() {
			public void windowClosing( WindowEvent e ) {
				try {
					TweedSettings.save(true);
					Tweed.deleteScratch();
				}
				catch (Throwable th) {
					th.printStackTrace();
				}
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
				
				if ( gmlCoordLabel != null && TweedSettings.settings.trans != null && pt != null) {
					worldLabel.setText( String.format( "world: %.4f, %.4f ", 
							pt.x, 
							pt.z) );
					
					gmlCoordLabel.setText( String.format( "%s: %.4f, %.4f ",
							TweedSettings.settings.gmlCoordSystem,
							pt.x + TweedSettings.settings.trans[0], 
							pt.z + TweedSettings.settings.trans[1]) );
					
					double[] latLong = tweed.worldToLatLong( pt );
					
					latLabel.setText( String.format( "%s: %.6f, %.6f ",
							"lat/long",
							latLong[0],
							latLong[1]
							) );
				}
				else 
				{
					worldLabel.setText( "?" );
					gmlCoordLabel.setText( "?" );
					latLabel.setText( "?" );
				}

				JFrame.setDefaultLookAndFeelDecorated( true );

			}
		}, 200, 200, TimeUnit.MILLISECONDS );

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

		out.setBorder( new EmptyBorder( 5, 5, 5, 5 ) );
		
		JMenuBar menuBar = new JMenuBar();
		frame.setJMenuBar( menuBar );

		//Build the first menu.
		JMenu menu = new JMenu( "file" );
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

		JMenuItem load = new JMenuItem( "open...", KeyEvent.VK_O );
		load.setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_O, ActionEvent.CTRL_MASK ) );
		menu.add(load);
		load.addActionListener( new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed( ActionEvent e ) {
					new SimpleFileChooser(frame, false, "select a tweed.xml", Tweed.DATA == null ? null : new File ( Tweed.DATA ), TWEED_XML) {
						
						@Override
						public void heresTheFile( File f ) throws Throwable {
							TweedSettings.load(f);
						}
					};
			}
		} );
		
		JMenuItem recent = new JLazyMenu( "open recent" ) {

			@Override
			public List<Runnable> getEntries() {

				List<Runnable> out = new ArrayList();

				for ( File r : TweedSettings.recentFiles.f ) {

					out.add( new Runnable() {
						@Override
						public void run() {
							if ( !r.exists() )
								JOptionPane.showMessageDialog( frame, "Location " + r.getName() + "not found (is it still there?)" );
							else
								TweedSettings.load( r );
						}

						@Override
						public String toString() {
							return r.getName();
						}
					} );

				}
				return out;
			}
		};
		menu.add(recent);

		JMenuItem neu = new JMenuItem( "new...", KeyEvent.VK_N );
		neu.setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_N, ActionEvent.CTRL_MASK ) );
		menu.add(neu);
		
		neu.addActionListener( new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed( ActionEvent e ) {
				
					new SimpleFileChooser(frame, false, "select a file in the root of the workspace") {
						@Override
						public void heresTheFile( File f ) throws Throwable {
							
							TweedSettings.folder = f.getParentFile();
							
							if (new File (TweedSettings.folder, TWEED_XML).exists()) {
								JOptionPane.showMessageDialog( frame, TWEED_XML + " already exists at this location, pick another (or delete...)" );
								return;
							}
							
							TweedSettings.load( TweedSettings.folder );
						}
					};
			}
		} );
		
//		JMenuItem remove = new JMenuItem( "delete layer", KeyEvent.VK_MINUS );
//		remove.setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_MINUS, ActionEvent.CTRL_MASK ) );
//		menu.add( remove );
//		remove.addActionListener( new java.awt.event.ActionListener() {
//			@Override
//			public void actionPerformed( ActionEvent e ) {
//				if ( selectedGen != null )
//					removeGen( selectedGen );
//			};
//		} );

		JMenuItem resetCam = new JMenuItem( "reset view", KeyEvent.VK_R );
		resetCam.setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_R, ActionEvent.CTRL_MASK ) );
		menu.add( resetCam );
		resetCam.addActionListener( new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed( ActionEvent e ) {
				tweed.resetCamera();
			};
		} );
		
		JMenuItem settings = new JMenuItem( "settings...", KeyEvent.VK_R );
		settings.setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_E, ActionEvent.CTRL_MASK ) );
		menu.add( settings );
		settings.addActionListener( new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed( ActionEvent e ) {
				new Auto( TweedSettings.settings, false ).frame();
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

		JMenuItem obj = new JMenuItem( "export obj...", KeyEvent.VK_E );
		obj.setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_E, ActionEvent.CTRL_MASK ) );
		menu.add( obj );
		obj.addActionListener( new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed( ActionEvent e ) {
				new SimpleFileChooser(frame, true, "save all as obj", new File (Tweed.SCRATCH, "all.obj"), "obj") {
					@Override
					public void heresTheFile( File f ) throws Throwable {

						ObjDump dump = new ObjDump();
						dump.REMOVE_DUPE_TEXTURES = true;

						for ( Gen g : genList )
							if ( g.visible && g instanceof IDumpObjs )
								( (IDumpObjs) g ).dumpObj( dump );
						
						dump.dump( f, new File ( Tweed.DATA ) );
					}
				}; 
			};
		} );

		layerList = new JPanel( new ListDownLayout() );

		JPanel layers = new JPanel();
		layers.setLayout( new BorderLayout() );
		layers.add( Fontz.setItalic( new JLabel( "layers:" ) ), BorderLayout.NORTH );

		JScrollPane listScroll = new JScrollPane( layerList, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER  );
		listScroll.getVerticalScrollBar().setUnitIncrement( 50 );
		listScroll.setPreferredSize( new Dimension( 200, 300 ) );
		layers.add( listScroll, BorderLayout.CENTER );

		JPanel addRemoveLayer = new JPanel();
		{
			addRemoveLayer.setLayout( new GridLayout( 1, 2 ) );
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

		JScrollPane optionsScroll;
		{
			JPanel options = new JPanel( new BorderLayout() );
			options.add( Fontz.setItalic( new JLabel( "options:" ) ), BorderLayout.NORTH );
			options.add( genUI, BorderLayout.CENTER );
			optionsScroll = new JScrollPane( options, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER  );
			optionsScroll .getVerticalScrollBar().setUnitIncrement( 50 );
		}

		JSplitPane pane = new JSplitPane( JSplitPane.VERTICAL_SPLIT, layers, optionsScroll );

		out.add( pane, BorderLayout.CENTER );

		JPanel toolPanel = new JPanel( new ListRightLayout() );

		tweed.addUI( toolPanel );

		gmlCoordLabel = new JLabel( "" );
		gmlCoordLabel.setHorizontalAlignment( SwingConstants.CENTER );
		worldLabel = new JLabel( "" );
		worldLabel.setHorizontalAlignment( SwingConstants.CENTER );
		latLabel = new JLabel( "none" );
		latLabel.setHorizontalAlignment( SwingConstants.CENTER );

		out.add( toolPanel, BorderLayout.NORTH );
		
		JPanel coords = new JPanel( new ListDownLayout() );
		coords.add( worldLabel );
		coords.add( gmlCoordLabel );
		coords.add( latLabel );
		
		out.add( coords, BorderLayout.SOUTH );

		out.setPreferredSize( new Dimension( 300, frame.getHeight() ) );
		
		return out;
	}

	JLabel gmlCoordLabel, latLabel, worldLabel;

	private void addLayer( MouseEvent evt ) {

		SimplePopup2 sp = new SimplePopup2( evt );

		if ( hasGIS() ) {
			sp.add( "+ mesh (obj)", new Runnable() {
				@Override
				public void run() {
					new SimpleFileChooser( frame, false, "Select .obj mesh file", new File( Tweed.JME ), "obj" ) {
						public void heresTheFile( File obj ) throws Throwable {
							//						removeMeshSources();

							String f = tweed.makeWorkspaceRelative( obj ).toString();
							addGen( new MeshGen( f, tweed ), true );
						};
					};
				}
			} );

			sp.add( "+ mesh (minimesh)", new Runnable() {
				@Override
				public void run() {
					new SimpleFileChooser( frame, false, "Select minimesh index file (index.xml), or obj to convert", new File( Tweed.JME ), null ) {
						@Override
						public void heresTheFile( File f ) throws Throwable {
							
							if ( !f.getName().equals( MiniTransform.INDEX ) ) {
								MiniTransform.convertToMini( f, new File( Tweed.DATA + "/minimesh" ),
										() -> addGen( new MiniGen( new File( "minimesh" ), tweed ), true ) );
								return;
							}
							
							//						removeMeshSources();
							addGen( new MiniGen( tweed.makeWorkspaceRelative( f.getParentFile() ), tweed ), true );
						}
					};
				}
			} );

			sp.add( "+ metadata", new Runnable() {
				@Override
				public void run() {
					new SimpleFileChooser( frame, false, "Select one of many csv files", new File( Tweed.JME ), "csv" ) {
						public void heresTheFile( File obj ) throws Throwable {
							addGen( new LotInfoGen( tweed.makeWorkspaceRelative( obj ), tweed ), true );
						};
					};
				}
			} );
			
			sp.add( "+ panos (jpg)", new Runnable() {
				@Override
				public void run() {
					new SimpleFileChooser( frame, false, "Select one of many panoramas images in a directory, or todo.list", new File( Tweed.JME ), null ) {
						public void heresTheFile( File oneOfMany ) throws Throwable {
							//						removeGens( PanoGen.class );
							addGen( new PanoGen( tweed.makeWorkspaceRelative( oneOfMany.getParentFile() ), tweed, Tweed.LAT_LONG ), true );
						};
					};
				}
			} );
			
			sp.add( "+ skel", new Runnable() {
				@Override
				public void run() {
					new SimpleFileChooser( frame, false, "Select skeleton to load", new File( Tweed.JME ), "xml" ) {
						public void heresTheFile( File skelGen ) throws Throwable {
							try {
								
									try {
										XStream xs = new XStream ();// new PureJavaReflectionProvider());
//										xs.ignoreUnknownElements();
										SkelGen sg = (SkelGen) xs.fromXML( skelGen );
										sg.onLoad( tweed );
										addGen( sg, true );
//										break;
									} catch ( Throwable th ) {
										th.printStackTrace();
									}
//							}
							}
							catch (Throwable th ) {
								th.printStackTrace();
								JOptionPane.showMessageDialog( frame, "failed to load "+skelGen.getName() );
							}
						};
					};
				}
			} );
			
			sp.add( "+ skels", new Runnable() {
				@Override
				public void run() {
					new SimpleFileChooser( frame, false, "Select one of many skels to load", new File( Tweed.JME ), "xml" ) {
						public void heresTheFile( File skelGen ) throws Throwable {
							try {

								for ( File f : skelGen.getParentFile().listFiles() ) {
									try {
										XStream xs = new XStream();// new PureJavaReflectionProvider());
										xs.ignoreUnknownElements();
										SkelGen sg = (SkelGen) xs.fromXML( f );
										sg.onLoad( tweed );
										addGen( sg, true );
									} catch ( Throwable th ) {
										th.printStackTrace();
									}
								}
							} catch ( Throwable th ) {
								th.printStackTrace();
								JOptionPane.showMessageDialog( frame, "failed to load " + skelGen.getName() );
							}
						};
					};
				}
			} );
			
		} else {

			sp.add( "+ gis (2d obj)", new Runnable() {
				@Override
				public void run() {
					new SimpleFileChooser( frame, false, "Select .obj gis footprints", new File( Tweed.JME ), "obj" ) {
						public void heresTheFile( File obj ) throws Throwable {
							removeGISSources();
							addGen( new GISGen( tweed.makeWorkspaceRelative( obj ), tweed ), true );
						};
					};
				}
			} );
			
//			sp.add( "+ gis (3d obj)", new Runnable() {
//				@Override
//				public void run() {
//					new SimpleFileChooser( frame, false, "Select .obj gis footprints", new File( Tweed.JME ), "obj" ) {
//						public void heresTheFile( File obj ) throws Throwable {
//							
//						};
//					};
//				}
//			} );

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
		}

		sp.show();
	}

	private boolean hasGIS() {
		return genList.stream().filter( g -> g instanceof GISGen ).findAny().isPresent();
	}

	protected void removeMeshSources() {
		removeGens( MiniGen.class );
		removeGens( ObjGen.class );
	}
	
	protected void removeGISSources() {
		TweedSettings.settings.resetTrans();
		removeGens( GISGen.class );
	}

	public void setGens( List<Gen> nGens ) {

		for ( Gen g : new ArrayList<Gen> ( genList ) )
			removeGen( g );

		layerList.removeAll();
		genList.clear();

		for ( Gen g : nGens ) {
			g.gNode = new Node();
			g.tweed = tweed;
		}

		for ( Gen g : nGens ) {
			g.onLoad( tweed );
			addGen( g, g.visible );
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
				gen.kill();
			}
		} );

		
		
		genList.remove( gen );

		Component togo = null;
		for ( Component c : layerList.getComponents() ) {
			if ( c instanceof GenListItem && ( (GenListItem) c ).gen == gen )
				togo = c;
		}

		if ( selectedGen == gen ) {
			selectedGen = null;
			setGenUI( null );
		}

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

		WindowManager.init( APP_NAME, "/org/twak/tweed/resources/icon512.png" );

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
		genUI.removeAll();
		if ( ui != null ) {
			genUI.setLayout( new BorderLayout() );
			genUI.add( ui, BorderLayout.CENTER );
		}
		
		genUI.revalidate();
		genUI.doLayout();
		genUI.repaint();

	}

	public List<Gen> gens( Class<? extends Gen> klass ) {
		return genList.stream().filter( g -> g.getClass() == klass ).collect( Collectors.toList() );
	}

	public <E extends Gen> E  getGenOf( Class<E> klass ) {
		
		List<Gen> gens = gens(klass);
		if (gens.isEmpty())
			return null;
		return (E) gens.get(0);
	}

}
