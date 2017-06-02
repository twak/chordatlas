package org.twak.tweed.gen;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.vecmath.Point2d;
import javax.vecmath.Point3d;

import org.twak.footprints.SatUtils;
import org.twak.tweed.ClickMe;
import org.twak.tweed.Tweed;
import org.twak.utils.collections.LoopL;
import org.twak.utils.collections.Loopz;
import org.twak.utils.geom.HalfMesh2;
import org.twak.utils.geom.ObjDump;
import org.twak.utils.geom.ObjRead;
import org.twak.viewTrace.GurobiSolver;
import org.twak.viewTrace.Slice;
import org.twak.viewTrace.SliceParameters;

import com.jme3.asset.ModelKey;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

public class BlockGen extends ObjGen {

//	ObjRead gis;
	File root;
	String selectedSelectedName = "";
	public LoopL<Point3d> polies;
	public ProfileGen profileGen;
	
	private static SliceParameters P = new SliceParameters(10); // when set by Slice UI, used for all future blocks!

	public Point2d center;
	
	public BlockGen( File l, Tweed tweed, LoopL<Point3d> polies ) {
		
		super ( new File(l, "cropped.obj").getPath().substring( Tweed.JME.length() ), tweed);
//		gis = new ObjRead( new File(l, "gis.obj") );
		this.polies = polies;
		this.root = l;
		this.name = "block";
		this.transparency = 1;
		
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
	
	boolean fireGenEvents = true;
	
	@Override
	public JComponent getUI() {
		
		JPanel panel = (JPanel) super.getUI();
		
		JComboBox<Selected> box = new JComboBox<>();
		
		box.addActionListener( new ActionListener() {
			@Override
			public void actionPerformed( ActionEvent e ) {
				if (fireGenEvents) {
					selectedSelectedName = ((Selected)box.getSelectedItem()).name;
					tweed.enqueue(new Runnable() {
						public void run() {
							((Selected)box.getSelectedItem()).onSelect();
						}
					} );
				}
			}
		} );
		
		fireGenEvents = false;
		
		box.addItem( new Selected("input") {
			@Override
			public void onSelect() {
				show ("cropped.obj");
			}
		});
		
		box.addItem( new Selected("slice") {
			@Override
			public void onSelect() {
				
				new Thread() {
					public void run() {

						File fs = getSlicedFile();

						if ( !fs.exists() ) 
						{
							new GurobiSolver( fs, 
									new Slice( 
											getCroppedFile(), 
											getGISFile(), P, false ), P );
						}

						show( "sliced.obj" );
					}


				}.start();
			}
		});
		
//		box.addItem( new Selected("skel") {
//			@Override
//			public void onSelect() {
//				tweed.tweed.addGen(new OldProfileGen( BlockGen.this, Loopz.toXZLoop( polies ), tweed), true);
//			}
//		});
		
		box.addItem( new Selected("profile") {
			@Override
			public void onSelect() {
				doProfile();
			}

		});
		
		for (int i = 0; i < box.getItemCount(); i++)
			if ( selectedSelectedName.equals( box.getItemAt( i ).name ) )
				box.setSelectedIndex( i );
		
		fireGenEvents = true;
		
//		box.addItem( new Selected("gis") {
//			@Override
//			public void onSelect() {
//				show ("gis.obj");
//			}
//		});
		
		JButton tooD = new JButton( "slice" );
		tooD.addActionListener( new ActionListener() {
			
			@Override
			public void actionPerformed( ActionEvent e ) {
				new Slice( root, ProfileGen.SLICE_SCALE );
			}
		} );
		
//		JButton windows = new JButton("wins");
//		windows.addActionListener( e -> tweed.frame.addGen(new WindowGen(tweed), true) );
		
		JButton features = new JButton("features");
		features.addActionListener( e -> tweed.frame.addGen ( new FeatureGen( new File ( Tweed.DATA+"/features/"), tweed ), false ) );

		JTextArea name = new JTextArea( nameCoords() );
		
		
		panel.add( box, 0 );
		panel.add( tooD, 1 );
//		panel.add( windows );
		panel.add( features );
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
		dump.setCurrentTexture( filename, 1, 1 );
		dump.addAll (getCroppedMesh());
	}
}
