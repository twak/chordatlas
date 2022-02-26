package org.twak.tweed.gen;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.vecmath.Matrix4d;
import javax.vecmath.Point2d;
import javax.vecmath.Point3d;
import javax.vecmath.Tuple3d;
import javax.vecmath.Vector3d;

import org.twak.readTrace.MiniTransform;
import org.twak.siteplan.jme.Jme3z;
import org.twak.tweed.EventMoveHandle;
import org.twak.tweed.Tweed;
import org.twak.tweed.TweedSettings;
import org.twak.tweed.handles.HandleMe;
import org.twak.tweed.tools.AlignTool;
import org.twak.utils.Filez;
import org.twak.utils.Pair;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.Loopz;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.geom.LinearForm3D;
import org.twak.utils.geom.ObjDump;
import org.twak.utils.geom.ObjDump.Face;
import org.twak.utils.streams.InaxPoint2dCollector;
import org.twak.utils.ui.ListDownLayout;

import com.jme3.asset.ModelKey;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Transform;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Mesh.Mode;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.thoughtworks.xstream.XStream;
import org.twak.utils.ui.SaveLoad;

public class MiniGen extends Gen implements HandleMe, ICanSave {
	
	File root;
	transient MiniTransform trans;

	boolean renderLines = false;
	float transparency = 1;
	
	transient List<double[]> bounds = new ArrayList();
	
	public MiniGen() {}
	
	public MiniGen(File root, Tweed tweed) {
		super (root.getName(), tweed);
		this.root = root;
		init();
	}

	@Override
	public void onLoad( Tweed tweed ) {
		super.onLoad( tweed );
		bounds = new ArrayList();
		init();
	}
	
	public void init() {
		trans = (MiniTransform) SaveLoad.createXStream().fromXML ( Tweed.toWorkspace( new File (root, "index.xml") ) );
	}

	@Override
	public void calculate( ) {
		
		for (Spatial s : gNode.getChildren() )
			s.removeFromParent();
		
		Material mat;
		if (transparency == 1) 
			mat = new Material(tweed.getAssetManager(), "Common/MatDefs/Light/Lighting.j3md");
		else {
			mat = new Material(tweed.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
			mat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
			mat.setColor("Color", new ColorRGBA( color.getRed()/255f, color.getGreen()/255f, color.getBlue()/255f, transparency ) );
		}
		
		for (Map.Entry<Integer, Matrix4d> e : trans.index.entrySet() ) {	
			
			if ( !inBounds( e.getValue(), bounds ) )
				continue;
			
			File absRoot = Tweed.toWorkspace( MiniGen.this.root );
			
			System.out.println("loading mesh " + e.getKey() +" from " + absRoot);
			File f = new File (absRoot, e.getKey() +"/model.obj" );
			
			Spatial mesh = tweed.getAssetManager().loadModel( tweed.makeWorkspaceRelative( f ).toString() );
			
			mesh.setLocalTransform( Jme3z.toJmeTransform( e.getValue() ) );
			
			Mode mode = renderLines ? Mesh.Mode.Lines : Mesh.Mode.Triangles;

			List<Spatial> ls;
			
			if (mesh instanceof Node)
				ls = ((Node)mesh).getChildren();
			else
				ls = Collections.singletonList( mesh );
			
			for ( Spatial g : ls ) {
				
				Geometry geometry = (Geometry) g;
				
				Mesh m = geometry.getMesh();
				
				m.setMode( mode );

				if (geometry.getMaterial().getName() == null)
					mesh.setMaterial( mat );

				gNode.attachChild( geometry );
				
				mesh.setUserData( Gen.class.getSimpleName(), new Object[] { this } );
			}
		}
		
		Transform t = new Transform();
		t.fromTransformMatrix( trans.offset );
		gNode.setLocalTransform( t );
		gNode.setUserData( Gen.class.getSimpleName(), new Object[] { this } );
		gNode.setUserData( HandleMe.class.getSimpleName(), true );
		gNode.setUserData( EventMoveHandle.class.getSimpleName(),new Object[] { new EventMoveHandle() {
			@Override
			public void posChanged() {
				MiniGen.this.save();
			}
		}} );
		
		gNode.updateGeometricState();
		
		super.calculate( );
	}

	public void save() {
		
		trans.offset = gNode.getLocalTransform().toTransformMatrix();
		System.out.println(trans.offset);
		
		new Thread() {
			public void run() {

				try {
					SaveLoad.createXStream().toXML( trans, new FileOutputStream( Tweed.toWorkspace( new File( root, "index.xml" ) ) ) );
					System.out.println("index file written");
				} catch ( FileNotFoundException e ) {
					e.printStackTrace();
				}
			};
		}.start();
	}
	
	
	private final static int MAX = 1000;
//	private static final boolean IMPORT_TEXTURES = true;

	@Override
	public JComponent getUI() {
		
		JPanel out = new JPanel(new ListDownLayout());

		JButton clear = new JButton("hide all");
		clear.addActionListener( new ActionListener() {
			
			@Override
			public void actionPerformed( ActionEvent e ) {
				bounds.clear();
				tweed.enqueue( new Runnable() {
					public void run() {
						calculate();
					};
				});
			}
		} );
		
		JButton all = new JButton("load all");
		all.addActionListener( new ActionListener() {
			
			@Override
			public void actionPerformed( ActionEvent e ) {
				bounds.clear();
				bounds.add(new double[] {-Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE } );
				tweed.enqueue( new Runnable() {
					public void run() {
						calculate();
					};
				});
			}
		} );
		
		final JCheckBox renderLines = new JCheckBox("wireframe");
		renderLines.setSelected(this.renderLines);
		
		renderLines.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {

				tweed.enqueue(new Callable<Spatial>() {
					public Spatial call() throws Exception {
						MiniGen.this.renderLines = renderLines.isSelected();
						calculate();
						return null;
					}
				});
			}
		});

		final JSlider renderTransparent = new JSlider(0, MAX, (int) (MAX * transparency));
		renderTransparent.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				tweed.enqueue(new Callable<Spatial>() {
					public Spatial call() throws Exception {
						MiniGen.this.transparency = (renderTransparent.getValue() / (float) MAX);
						calculate();
						return null;
					}
				});
			}
		});

		JButton align = new JButton("align tool");
		align.addActionListener( e -> tweed.setTool(new AlignTool(tweed)) );
		
		out.add(all);
		out.add(clear);
		out.add(renderLines);
		out.add(renderTransparent);
		out.add(align);
		
		return out;
	}

	public void moveTo( Transform t ) {
		
		tweed.enqueue( new Runnable() {
			public void run() {
				gNode.setLocalTransform( t );
				tweed.gainFocus();
			}
		} );
	}

	public void addBounds( double lx, double ux, double ly, double uy ) {
		
		double[] b = new double[] { lx, ux, ly, uy };
		
		for (double[] b2 : bounds)
			if (b2.equals( b ))
				return;
		
		bounds.add (b);
		
		calculate();
	}
	
	public void clip ( Loop<Point3d> in, File objLocation ) {
		
		ObjDump obj = new ObjDump();
		obj.REMOVE_DUPE_TEXTURES = true;
		
		double[] bounds = Loopz.minMaxXZ(in);
		
		List<LinearForm3D> halfPlanes = new ArrayList();
		
		File writeFolder = objLocation.getParentFile();
		
		for (Pair<Point3d, Point3d> p : in.pairs()) {
			
			Vector3d norm = new Vector3d( p.second() );
			norm.sub(p.first());
			norm = new Vector3d( -norm.z, 0, norm.x);
			norm.normalize();
			
			halfPlanes.add ( new LinearForm3D( norm, p.first() ) );
		}
		
		Map<File,File> copied = new HashMap<>();
		int nameCount = 0;
		
		double minY =  Double.MAX_VALUE, 
			   maxY = -Double.MAX_VALUE;
		
		for ( Map.Entry<Integer, Matrix4d> e : trans.index.entrySet() ) {

			if ( !inBounds( e.getValue(), Collections.singletonList( bounds ) ) )
				continue;
			else
			{
				Matrix4d m = new Matrix4d();
				m.mul( Jme3z.fromMatrix( trans.offset ), e.getValue() );

				File readFolder = new File( Tweed.toWorkspace( root ), e.getKey() + "" );
				ObjDump or = new ObjDump( new File( readFolder, "model.obj" ) );

				or.computeMissingNormals();
				
				for ( ObjDump.Material mat : or.material2Face.keySet() ) {

					f:
					for ( Face f : or.material2Face.get( mat ) ) {

						for ( int j = 0; j < f.vtIndexes.size(); j++ ) {

							Point3d pt = new Point3d( or.orderVert.get( f.vtIndexes.get( j ) ) );
							
							m.transform( pt );

							if ( pt.x > bounds[ 0 ] && pt.x < bounds[ 1 ] && pt.z > bounds[ 2 ] && pt.z < bounds[ 3 ] )
								if ( inside( pt, halfPlanes ) ) 
								{

									if ( TweedSettings.settings.importMiniMeshTextures && ! (
											(obj.currentMaterial != null && obj.currentMaterial.equals( mat ) ) ||
											(obj.currentMaterial == null && mat == null ) ) ) {
										
										File source = new File( readFolder, mat.filename );
										
										ObjDump.Material newMat;
										if (copied.containsKey( source )) {
											
											newMat = new ObjDump.Material(mat);
											newMat.filename = copied.get(source).getName();
											
										} else {
											
											newMat = makeUnique (mat, writeFolder);
											
											File destFile = new File( writeFolder, newMat.filename );
											copied.put( source, destFile );
											
											try {
												Files.copy( source.toPath(), 
														new FileOutputStream( destFile ) );
											} catch ( IOException e1 ) {
												e1.printStackTrace();
											}
											
										}
										
										newMat.diffuse  = new double[] {0,0,0};
										newMat.ambient  = new double[] {1,1,1};
										newMat.specular = new double[] {0,0,0};
										
										newMat.name = "mat_"+(nameCount++);
										obj.setCurrentMaterial( newMat );
									}

									List<Point3d> fVerts = new ArrayList<>(3), fNorms = new ArrayList<>(3);
									
									List<Point2d> fUVs = null;
									if (fUVs != null)
										fUVs = new ArrayList<>(2);
									
									for ( int i = 0; i < f.vtIndexes.size(); i++ ) {

										Point3d vts = new Point3d( or.orderVert.get( f.vtIndexes.get( i ) ) );
										Point3d ns  = new Point3d( or.orderNorm.get( f.normIndexes.get( i ) ) );

										ns.add( vts );

										m.transform( vts );

										m.transform( ns );
										ns.sub( vts );
										
										minY = Math.min (vts.y, minY);
										maxY = Math.max (vts.y, maxY);
										
										fVerts.add( vts );
										fNorms.add( ns );
										if (fUVs != null)
											fUVs.add (new Point2d( or.orderUV.get( f.uvIndexes.get( i ) ) ));
									}

									obj.addFace( fVerts, fNorms, fUVs );

									continue f;
								}
						}
					}
				}
			}
		}
		
			for ( Tuple3d t : obj.orderVert )
				t.y -=  (maxY - minY) * 0.03 + minY;
		
		obj.dump( objLocation );
	}

	private ObjDump.Material makeUnique( ObjDump.Material mat, File writeFolder ) {
		
		ObjDump.Material out = new ObjDump.Material( mat );
		int ind = 0;
		String newName;
		
		while (new File (writeFolder, newName = String.format( "%05d."+Filez.getExtn(mat.filename), ind )).exists())
			ind++;
		
		out.filename = newName;
		
		return out;
	}

	private boolean inside( Point3d pt, List<LinearForm3D> halfPlanes ) {
		
		for (LinearForm3D lf : halfPlanes)
			if (!lf.inFront( pt ))
				return false;
		
		return true;
	}

	final static Point3d[] cubeCorners = new Point3d[] {
			new Point3d(0,0,0),
			new Point3d(255,0,0),
			new Point3d(255,255,0),
			new Point3d(0,255,0),
			new Point3d(0,0,255),
			new Point3d(255,0,255),
			new Point3d(0,255,255),
			new Point3d(255,255,255),
	} ;
	
	
	
	private boolean inBounds( Matrix4d mini, List<double[]> bounds ) {
		
		// mini matrix is in mini-mesh format: a translation from a 255^3 cube in the first quadrant
		// trans.offset is a transform from that space, into jme rendered space (cartesian in meters, around the origin)
		
		Matrix4d m = new Matrix4d();
		m.mul( Jme3z.fromMatrix ( trans.offset ), mini );
		
		double[] miniBounds = Arrays.stream( cubeCorners ).map( c -> { 
			Point3d tmp = new Point3d();
			m.transform( c, tmp );
			return new Point2d(tmp.x, tmp.z);
		}).collect( new InaxPoint2dCollector() );
		
		DRectangle miniRect = new DRectangle( miniBounds[0], miniBounds[2], miniBounds[1]-miniBounds[0], miniBounds[3]-miniBounds[2] );
		
		for (double[] bound : bounds) {
			DRectangle plotRect = new DRectangle( bound[0], bound[2], bound[1]-bound[0], bound[3]-bound[2] );
			if (plotRect.intersects( miniRect ))
				return true;
		}
		
		return false;
	}

	@Override
	public void kill() {
		for ( Map.Entry<Integer, Matrix4d> e : trans.index.entrySet() ) {

			if ( !inBounds( e.getValue(), bounds ) )
				continue;

			File absRoot = Tweed.toWorkspace( MiniGen.this.root );

			System.out.println( "loading mesh " + e.getKey() + " from " + absRoot );
			File f = new File( absRoot, e.getKey() + "/model.obj" );

			tweed.getAssetManager().deleteFromCache( new ModelKey ( tweed.makeWorkspaceRelative( f ).toString() ) );
		}
	}

}
