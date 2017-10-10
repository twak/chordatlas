package org.twak.tweed.gen;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.vecmath.Point2d;
import javax.vecmath.Point3d;

import org.twak.tweed.ClickMe;
import org.twak.tweed.Tweed;
import org.twak.utils.collections.LoopL;
import org.twak.utils.collections.Loopz;
import org.twak.utils.geom.ObjDump;
import org.twak.utils.geom.ObjRead;
import org.twak.utils.geom.HalfMesh2.HalfFace;
import org.twak.viewTrace.Slice;
import org.twak.viewTrace.SliceParameters;
import org.twak.viewTrace.SliceSolver;

import com.jme3.asset.ModelKey;
import com.jme3.scene.Spatial;
import com.thoughtworks.xstream.XStream;

public class BlockGen extends ObjGen {

	File root;
	String selectedSelectedName = "";
	public LoopL<Point3d> polies;
	public ProfileGen profileGen;
	
	private static SliceParameters P = new SliceParameters(10); // when set by Slice UI, used for all future blocks!

	public Point2d center;
	
	public BlockGen( File l, Tweed tweed, LoopL<Point3d> polies ) {
		
		super ( new File(l, GISGen.CROPPED_OBJ ).getPath().substring( Tweed.JME.length() ), tweed);

		this.polies = polies;
		this.root = l;
		this.name = "block";
		this.transparency = 0;
		
		this.center = Loopz.average( Loopz.to2dLoop( polies, 1, null ) );
		System.out.println("creating block with name: " + nameCoords() );
	}
	
	@Override
	public void calculate() {
		
		super.calculate();
		doClicked(gNode);
	}

	private void doClicked( Spatial s ) {
		s.setUserData( ClickMe.class.getSimpleName(), new Object[] { new ClickMe() {
			@Override
			public void clicked( Object data ) {
				doProfile();
			}
		} } );
	}

	private void show (String file) {
		String full = new File (root, file).getPath();
		String neuFilename = full.substring( Tweed.JME.length() );
		
		if (!neuFilename.equals( filename )) {
			filename = neuFilename;
			tweed.getAssetManager().deleteFromCache( new ModelKey( filename ) );
			calculateOnJmeThread();
		}
	}
	
	@Override
	public JComponent getUI() {
		
		JPanel panel = (JPanel) super.getUI();

		JButton profiles = new JButton ("profiles");
		profiles.addActionListener( e -> doProfile() );
		
		JButton slice = new JButton ("slice");
		slice.addActionListener( new ActionListener() {
			
			@Override
			public void actionPerformed( ActionEvent e ) {
				new Thread() {
					public void run() {

						File fs = getSlicedFile();

						if ( !fs.exists() ) 
						{
							new SliceSolver( fs, 
									new Slice( 
											getCroppedFile(), 
											getGISFile(), P, false ), P );
						}
						
						tweed.frame.addGen( new ObjGen( 
								tweed.toAssetManager( fs ).toString(),
								tweed ), true); 
					}

				}.start();
			}
		} );
		
		JButton tooD = new JButton( "slice UI" );
		tooD.addActionListener( new ActionListener() {
			@Override
			public void actionPerformed( ActionEvent e ) {
				new Slice( root, ProfileGen.SLICE_SCALE );
			}
		} );
		
		JButton loadSln = new JButton( "load sln" );
		loadSln.addActionListener( new ActionListener() {
			@Override
			public void actionPerformed( ActionEvent e ) {
				File f = getSolutionFile();
				if ( f.exists() ) {

					SolverState SS = (SolverState) new XStream().fromXML( f );
					SkelFootprint.postProcesss( SS );
					tweed.frame.addGen( new SkelGen( SS.mesh, tweed, BlockGen.this ), true );
				} else {
					JOptionPane.showMessageDialog( tweed.frame(), "Unable to find pre-computed solution.\n" + f );
				}
			}
		} );
		
		JTextArea name = new JTextArea( nameCoords() );
		
		panel.add(profiles,0 );
		panel.add( slice, 1 );
		panel.add( tooD );
		panel.add( loadSln );
		panel.add( name );
		
		return panel;
	}

	public String nameCoords() {
		return center.x+"_"+center.y;
	}
	
	private void doProfile() {
		new Thread() {
			@Override
			public void run() {
				tweed.frame.addGen( profileGen = new ProfileGen(BlockGen.this, Loopz.toXZLoop( polies ), tweed), true);
			}
		}.start();
	}
	
	public File getGISFile() {
		return new File( root, "gis.obj" );
	}

	public File getCroppedFile() {
		return new File( root, "cropped.obj" );
	}
	
	public File getSlicedFile() {
		return new File( root, "sliced.obj" );
	};
	
	private static abstract class Selected {
		
		String name;
		
		public Selected(String name) {
			this.name = name;
		}
		
		public abstract void onSelect();
		
		@Override
		public String toString() {
			return name;
		}
	}

	ObjRead croppedMesh = null;
	public ObjRead getCroppedMesh() {
		
		if (croppedMesh == null)
			croppedMesh = new ObjRead( getCroppedFile() );

		return croppedMesh;
	}
	
	double[] croppedExtent = null;
	public double[] getCroppedExtent() {
		if (croppedExtent == null)
			croppedExtent = getCroppedMesh().findExtent();
		
		return croppedExtent;
	}
	
	@Override
	public void dumpObj( ObjDump dump ) {
		dump.setCurrentMaterial( Color.blue, 0.5);
		dump.addAll (getCroppedMesh());
	}

	public File getInputFolder( String dir ) {
		return new File (Tweed.DATA, dir+File.separator+nameCoords() );
	}

	public File getSolutionFile() {
		return new File (getInputFolder(ResultsGen.SOLUTIONS),ResultsGen.SOLVER_FILE);
	}
}
