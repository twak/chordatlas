package org.twak.tweed.plugins;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.swing.JOptionPane;

import org.twak.readTrace.MiniTransform;
import org.twak.tweed.Tweed;
import org.twak.tweed.TweedFrame;
import org.twak.tweed.TweedSettings;
import org.twak.tweed.gen.GISGen;
import org.twak.tweed.gen.LotInfoGen;
import org.twak.tweed.gen.MeshGen;
import org.twak.tweed.gen.MiniGen;
import org.twak.tweed.gen.ObjGenList;
import org.twak.tweed.gen.PanoGen;
import org.twak.tweed.gen.skel.SkelGen;
import org.twak.tweed.tools.FacadeTool;
import org.twak.tweed.tools.HouseTool;
import org.twak.tweed.tools.MoveTool;
import org.twak.tweed.tools.SelectTool;
import org.twak.tweed.tools.Tool;
import org.twak.utils.Filez;
import org.twak.utils.ui.SimpleFileChooser;
import org.twak.utils.ui.SimplePopup2;

import com.google.common.io.Files;
import com.thoughtworks.xstream.XStream;

public class HousesPlugin implements TweedPlugin {

	
	@Override
	public void addTools( Tweed tweed, List<Tool> tools ) {
		tools.add( new SelectTool(tweed) );
		tools.add( new MoveTool(tweed) );
//		tools.add( new AlignTool(tweed) );
		tools.add( new FacadeTool(tweed) );
		tools.add( new HouseTool(tweed) );
//		tools.add( new PlaneTool(tweed) );
//		tools.add( new TextureTool(tweed) );	
	}
	
	public void addToAddMenu(TweedFrame tf, SimplePopup2 sp ) {

		if ( tf.hasGIS() ) {
		
			sp.add( "+ mesh (obj)", new Runnable() {
				@Override
				public void run() {
					new SimpleFileChooser( tf.frame, false, "Select .obj mesh file", new File( Tweed.JME ), "obj" ) {
						public void heresTheFile( File obj ) throws Throwable {
							//						removeMeshSources();

							obj = queryImport (tf, obj);
							if ( obj != null ) {
								String f = tf.tweed.makeWorkspaceRelative( obj ).toString();
								tf.addGen( new MeshGen( f, tf.tweed ), true );
							}
						}
					};
				}
			} );

			sp.add( "+ mesh (minimesh)", new Runnable() {
				@Override
				public void run() {
					new SimpleFileChooser( tf.frame, false, "Select minimesh index file (index.xml), or obj to convert", new File( Tweed.JME ), null ) {
						@Override
						public void heresTheFile( File f ) throws Throwable {
							
							if ( !f.getName().equals( MiniTransform.INDEX ) ) {
								MiniTransform.convertToMini( f, new File( Tweed.DATA + "/minimesh" ),
										() -> tf.addGen( new MiniGen( new File( "minimesh" ), tf.tweed ), true ) );
								return;
							}
							
							//						removeMeshSources();
							tf.addGen( new MiniGen( tf.tweed.makeWorkspaceRelative( f.getParentFile() ), tf.tweed ), true );
						}
					};
				}
			} );

			sp.add( "+ meshes (obj via list)", new Runnable() {
				@Override
				public void run() {
					new SimpleFileChooser( tf.frame, false, "Select todo.list", new File( Tweed.JME ), "list" ) {
						public void heresTheFile( File todoFile ) throws Throwable {
							File folder = tf.tweed.makeWorkspaceRelative( todoFile.getParentFile() );
							System.out.println("added folder of " + folder.getName());
							tf.addGen( new ObjGenList( todoFile, folder, tf.tweed ), true );
						}
					};
				}
			} );
			
			
			sp.add( "+ metadata", new Runnable() {
				@Override
				public void run() {
					new SimpleFileChooser( tf.frame, false, "Select one of many csv files", new File( Tweed.JME ), "csv" ) {
						public void heresTheFile( File obj ) throws Throwable {
							tf.addGen( new LotInfoGen( tf.tweed.makeWorkspaceRelative( obj ), tf.tweed ), true );
						};
					};
				}
			} );
			
			sp.add( "+ panos (jpg)", new Runnable() {
				@Override
				public void run() {
					new SimpleFileChooser( tf.frame, false, "Select one of many panoramas images in a directory, or todo.list", new File( Tweed.JME ), null ) {
						public void heresTheFile( File oneOfMany ) throws Throwable {
							//						removeGens( PanoGen.class );
							tf.addGen( new PanoGen( tf.tweed.makeWorkspaceRelative( oneOfMany.getParentFile() ), tf.tweed, Tweed.LAT_LONG ), true );
						};
					};
				}
			} );
			
			sp.add( "+ skel", new Runnable() {
				@Override
				public void run() {
					new SimpleFileChooser( tf.frame, false, "Select skeleton to load", new File( Tweed.JME ), "xml" ) {
						public void heresTheFile( File skelGen ) throws Throwable {
							try {
								
									try {
										XStream xs = new XStream ();// new PureJavaReflectionProvider());
//										xs.ignoreUnknownElements();
										SkelGen sg = (SkelGen) xs.fromXML( skelGen );
										sg.onLoad( tf.tweed );
										tf.addGen( sg, true );
//										break;
									} catch ( Throwable th ) {
										th.printStackTrace();
									}
//							}
							}
							catch (Throwable th ) {
								th.printStackTrace();
								JOptionPane.showMessageDialog( tf.frame, "failed to load "+skelGen.getName() );
							}
						};
					};
				}
			} );
			
			sp.add( "+ skels", new Runnable() {
				@Override
				public void run() {
					new SimpleFileChooser( tf.frame, false, "Select one of many skels to load", new File( Tweed.JME ), "xml" ) {
						public void heresTheFile( File skelGen ) throws Throwable {
							try {

								for ( File f : skelGen.getParentFile().listFiles() ) {
									try {
										XStream xs = new XStream();// new PureJavaReflectionProvider());
										xs.ignoreUnknownElements();
										SkelGen sg = (SkelGen) xs.fromXML( f );
										sg.onLoad( tf.tweed );
										tf.addGen( sg, true );
									} catch ( Throwable th ) {
										th.printStackTrace();
									}
								}
							} catch ( Throwable th ) {
								th.printStackTrace();
								JOptionPane.showMessageDialog( tf.frame, "failed to load " + skelGen.getName() );
							}
						};
					};
				}
			} );
			
		} else {

			sp.add( "+ gis (2d obj)", new Runnable() {
				@Override
				public void run() {
					new SimpleFileChooser( tf.frame, false, "Select .obj gis footprints", new File( Tweed.JME ), "obj" ) {
						public void heresTheFile( File obj ) throws Throwable {
							removeGISSources(tf);
							tf.addGen( new GISGen( tf.tweed.makeWorkspaceRelative( obj ), tf.tweed ), true );
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

					new SimpleFileChooser( tf.frame, false, "Select .gml gis footprints", new File( Tweed.JME ), "gml" ) {
						public void heresTheFile( File gml ) throws Throwable {
							removeGISSources(tf);
							tf.tweed.addGML( gml, null );
						};
					};
				}
			} );
		}

	}
	
	private File queryImport( TweedFrame tf, File obj_ ) {
		
		File obj = obj_;
		File c = obj.getParentFile();
		boolean okay = false;
		
		while (c!= null) {
			if (c.equals( new File ( Tweed.DATA )) )
				okay = true;
				c = c.getParentFile();
		}
		
		if (okay)
			return obj;
		
		if (JOptionPane.showConfirmDialog( tf.frame, obj.getName() +" is outside project; import?", "import", JOptionPane.OK_CANCEL_OPTION ) == JOptionPane.OK_OPTION) {
			try {
				Files.copy( obj, obj = Filez.makeUnique( new File (Tweed.DATA, obj.getName() ) ) );
				return obj;
			} catch ( IOException e ) {
				e.printStackTrace();
			}
		}
		return null;
	}
	
	protected void removeGISSources(TweedFrame tf) {
		TweedSettings.settings.resetTrans();
		tf.removeGens( GISGen.class );
	}

	@Override
	public void addToNewScene( TweedFrame instance ) {
		instance.addGen( new GISGen( TweedFrame.instance.tweed ), true );		
	}
	
}
