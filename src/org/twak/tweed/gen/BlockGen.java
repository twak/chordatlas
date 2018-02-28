package org.twak.tweed.gen;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.vecmath.Point2d;
import javax.vecmath.Point3d;

import org.twak.tweed.ClickMe;
import org.twak.tweed.Tweed;
import org.twak.tweed.gen.GISGen.Mode;
import org.twak.tweed.tools.FacadeTool;
import org.twak.utils.collections.LoopL;
import org.twak.utils.collections.Loopz;
import org.twak.utils.collections.Streamz;
import org.twak.utils.geom.ObjDump;
import org.twak.utils.geom.ObjRead;
import org.twak.viewTrace.FacadeFinder;
import org.twak.viewTrace.FacadeFinder.FacadeMode;
import org.twak.viewTrace.facades.AlignStandalone2d;
import org.twak.viewTrace.Slice;
import org.twak.viewTrace.SliceParameters;
import org.twak.viewTrace.SliceSolver;

import com.google.common.io.FileWriteMode;
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
				tweed.frame.setSelected( BlockGen.this );
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

		JButton profiles = new JButton ("find profiles");
		profiles.addActionListener( e -> doProfile() );
		
		JButton panos = new JButton ("render panoramas");
		panos.addActionListener( e -> renderPanos() );
		
		JButton features = new JButton ("find image features");
		features.addActionListener( e -> segnetFacade() );
		
		JButton viewFeatures = new JButton ("features viewer");
		viewFeatures.addActionListener( e -> viewFeatures() );
		
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
								tweed.makeWorkspaceRelative( fs ).toString(),
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
		name.setEditable( false );
		
		panel.add(profiles, 0 );
		panel.add(panos, 1 );
		panel.add(features, 2 );
		panel.add(new JLabel("other:"), 4 );
		panel.add( slice );
		panel.add( viewFeatures );
//		panel.add( tooD );
		if (getSolutionFile().exists())
			panel.add( loadSln );
		panel.add(new JLabel("name:") );
		panel.add( name );
		
		return panel;
	}

	private void viewFeatures() {
		AlignStandalone2d.show( getInputFolder( FeatureCache.FEATURE_FOLDER ).toString() );
	}

	private void segnetFacade() {
		
		File r = getInputFolder( FeatureCache.FEATURE_FOLDER );
		
		if (!r.exists()) {
			JOptionPane.showMessageDialog( tweed.frame(), "no facade images found - have they been rendered?" );
			return;
		}
			
		
		File toProcess = new File (r, "files.txt");
		
		if (toProcess.exists())
			toProcess.delete();
		
		boolean[] seenResult = new boolean[] {false};
		
		StringBuffer sb = new StringBuffer();
		
		FileVisitor<Path> fv = new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile( Path file, BasicFileAttributes attrs ) throws IOException {
				File f = file.toFile();
				File result = new File ( f.getParentFile(), FeatureCache.PARAMETERS_YML );
				
				if (f.getName().equals( FeatureCache.RENDERED_IMAGE_PNG ) && !result.exists() ) {
					
					Path fileR = r.toPath().relativize( file ),
							resultR = r.toPath().relativize( result.toPath() );
					
					
					seenResult[0] |= resultR.toFile().exists();
					
					sb.append ( "/output/" + fileR +"\t"+
							"/output/" +resultR +"\n" );
				}
				
				return FileVisitResult.CONTINUE;
			}
		};

		
		try {
			Files.walkFileTree( r.toPath(), fv );
			FileWriter fw = new FileWriter( toProcess );
			fw.append( sb );
			fw.close();
		} catch ( IOException e ) {
			e.printStackTrace();
		}
		
		if (sb.length() == 0) {
			JOptionPane.showMessageDialog( tweed.frame(), "all features already computed. nothing to do here!" );
			return;
		}
		
		System.out.println( "running CNN to find features..." );
		
		ProcessBuilder pb = new ProcessBuilder( 
				"nvidia-docker", "run", "-v", r+":/output",
				"twak/segnet-facade", "bash", "-c", "source inference /output/files.txt" );
		
		Process p;
		
		try {
			p = pb.start();
			Streamz.inheritIO(p.getInputStream(), System.out);
			Streamz.inheritIO(p.getErrorStream(), System.err);
		} catch ( IOException e ) {
			e.printStackTrace();
		}
		
	}

	private void renderPanos() {
		
		if (getInputFolder( FeatureCache.FEATURE_FOLDER ).exists()) {
			int result = JOptionPane.showConfirmDialog(tweed.frame(), "feature folder already exists. really re-render?",
			        "alert", JOptionPane.OK_CANCEL_OPTION);
			if (result == JOptionPane.CANCEL_OPTION)
				return;
		}
		
		GISGen.mode = Mode.RENDER_SELECTED_BLOCK;
		FacadeFinder.facadeMode = FacadeMode.PER_CAMERA;
		
		new FacadeTool(tweed).facadeSelected( polies, this );
	}

	public String nameCoords() {
		return center.x+"_"+center.y;
	}
	
	private void doProfile() {
		new Thread() {
			@Override
			public void run() {
				tweed.frame.addGen( profileGen = new ProfileGen(BlockGen.this, Loopz.toXZLoop( polies ), tweed), true);
				tweed.frame.setSelected( profileGen );
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
