package org.twak.tweed.gen;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;

import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeocentricCRS;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.twak.readTrace.Mosaic;
import org.twak.siteplan.jme.Jme3z;
import org.twak.tweed.ClickMe;
import org.twak.tweed.EventMoveHandle;
import org.twak.tweed.IDumpObjs;
import org.twak.tweed.Tweed;
import org.twak.tweed.TweedSettings;
import org.twak.tweed.tools.FacadeTool;
import org.twak.tweed.tools.PlaneTool;
import org.twak.utils.Filez;
import org.twak.utils.Mathz;
import org.twak.utils.geom.ObjDump;
import org.twak.utils.ui.ListDownLayout;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.math.Vector4f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Mesh.Mode;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Box;
import com.jme3.util.BufferUtils;
import com.thoughtworks.xstream.XStream;

public class PanoGen extends Gen implements IDumpObjs, ICanSave {
	
	File folder;
	public transient List<Pano> panos = new ArrayList();
	
	public String sourceCRS;
	
	transient Pano selectedPano = null;
	transient JPanel ui = new JPanel();
	
	public  transient List<ImagePlaneGen> planes = new ArrayList<>();
	
	public PanoGen() {}
	
	public PanoGen(File folder, Tweed tweed, String sourceCRS ) {
		super ("panos "+folder.getName(), tweed);
		this.folder = folder;
		this.sourceCRS = sourceCRS;
	}
		
	public PanoGen( Tweed tweed ) {
		super ("render progress", tweed);
		this.tweed = tweed;
		this.folder = null;
	}
	
	@Override
	public void calculate( ) {
		
		
		if ( folder != null ) {
			File absFolder = Tweed.toWorkspace( folder );

			if ( !absFolder.exists() )
				throw new Error( "File not found " + this.folder );
		}
		
		for (Spatial s : gNode.getChildren())
			s.removeFromParent();
				
		createPanoGens();
		
		Iterator<Pano> pit = panos.iterator();
		
		while (pit.hasNext()) {
			Pano p = pit.next();
			if (p.rx == 0 && Math.abs ( p.rz - Mathz.TwoPI ) < 1e-6)
				pit.remove();
		}
		
		Random randy = new Random (0xdeadbeef);
		
		for (Pano p : panos) {
			if (p.geom == null) {
				
				Box box1 = new Box(1f, 1f, 1f);
				p.geom = new Geometry("Box", box1);

//				p.geom.setUserData(Gen.class.getSimpleName(), new Object[]{this});
				
				p.geom.setUserData(EventMoveHandle.class.getSimpleName(), new Object[] { new EventMoveHandle() {
					@Override
					public void posChanged() {
						p.location = new Vector3d( Jme3z.from ( p.geom.getLocalTranslation() ) );
						calculate();
					}
				} });
				
//				Material mat1 = new Material(tweed.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
				
				ColorRGBA col = new ColorRGBA( 
						color.getRed()   * (0.2f + randy.nextFloat()*0.8f) / 255f, 
						color.getGreen() * (0.2f + randy.nextFloat()*0.8f) / 255f, 
						color.getBlue()  * (0.2f + randy.nextFloat()*0.8f) / 255f, 1f );
				
				Material mat = new Material( tweed.getAssetManager(), "Common/MatDefs/Light/Lighting.j3md" );
				mat.setColor( "Diffuse", col );
				mat.setColor( "Ambient", col );
				mat.setBoolean( "UseMaterialColors", true );
				
				p.geom.setMaterial(mat);
			}
			
			p.geom.setLocalTranslation( (float) p.location.x, (float) p.location.y, (float) p.location.z);
			p.geom.setLocalRotation( p.geomRot ); 
			
			p.geom.setUserData( ClickMe.class.getSimpleName(), new Object[] { new ClickMe() {
				@Override
				public void clicked( Object data ) {
					tweed.frame.setSelected( PanoGen.this );
					selected(p);
				}
			} } );
	        
	        gNode.attachChild(p.geom);
		}
		
		for (ImagePlaneGen ipg : planes) {
			ipg.calculate();
			gNode.attachChild( ipg.gNode );
		}

		super.calculate();
	}
	
	protected void createPanoGens() {

		panos.clear();
		
		File meta = getMetaFile();

		panos = null;
		
		if ( meta.exists() ) 
		{
			try {
				panos = (List<Pano>) new XStream().fromXML( meta );
			} catch ( Throwable th ) {
				th.printStackTrace();
			}
		}
		
		
		if (panos == null || panos.isEmpty()) 
		{
			panos = new ArrayList<>();
			
			for ( File f : Tweed.toWorkspace( folder).listFiles() ) {
				String extn = Filez.getExtn( f.getName() );
				if ( extn.equals( "jpg" ) || extn.equals( "png" ) )
					createPanoGen( f, panos );
			}
			
			try {
				
				
				for ( Pano p : panos ) {
					if (p.orig.isAbsolute())
						p.orig = tweed.makeWorkspaceRelative( p.orig );
				}
				
				if (!panos.isEmpty())
					new XStream().toXML( panos, new FileOutputStream( meta ) );
				
			} catch ( FileNotFoundException e ) {
				e.printStackTrace();
			}
		}
	}

	private File getMetaFile() {
		return Tweed.toWorkspace( new File( folder, "panos.xml" ) );
	}

	private void createPanoGen( File f, List<Pano> results ) {
		results.add( createPanoGen( f, sourceCRS  ) );
	}
	
	public static Pano createPanoGen( File f, String sourceCRS ) {
		String name = f.getName().substring( 0, f.getName().length() - 4 );
		try
		{
			String[] sVals = name.split( "[_]", 10 );
			
			if (sVals.length < 6)
				return null;
			
			List<Double> pos = Arrays.asList( Arrays.copyOfRange( sVals, 0, 6 ) )
					.stream().map( z -> Double.parseDouble( z ) ).collect( Collectors.toList() );
			
			double[] trans = new double[] { pos.get( 0 ), pos.get( 1 ), 0 };
			double[] north = new double[] { pos.get( 0 ), pos.get( 1 ) + 1e-6, 0 };
			// two part transform to align heights - geoid for 4326 is different to 27700
			
			MathTransform latLong2Country = CRS.findMathTransform( 
					CRS.decode( sourceCRS ), 
					Tweed.kludgeCMS.get(TweedSettings.settings.gmlCoordSystem),
					true );
			
			
			latLong2Country.transform( trans, 0, trans, 0, 1 );
			latLong2Country.transform( north, 0, north, 0, 1 );
			
			if (TweedSettings.settings.gmlCoordSystem.equals ("EPSG:3042") ) { /* madrid?! */
				System.out.println("******* dirty hack in place for flipped CS");
				double tmp = trans[0];
				trans[0] = trans[1];
				trans[1] = tmp;
			}
			
			MathTransform country2Cartesian = CRS.findMathTransform( Tweed.kludgeCMS.get( TweedSettings.settings.gmlCoordSystem ),  DefaultGeocentricCRS.CARTESIAN, true );
			country2Cartesian.transform( trans, 0, trans, 0, 1 );
			country2Cartesian.transform( north, 0, north, 0, 1 );

			{
				Point3d tmp = new Point3d(trans);
				TweedSettings.settings.toOrigin.transform( tmp );
				tmp.get( trans );
				
				tmp = new Point3d(north);
				TweedSettings.settings.toOrigin.transform( tmp );
				tmp.get( north );
			}
			
			if (TweedSettings.settings.gmlCoordSystem.equals ("EPSG:2062") ) { // oviedo :(
				trans[2] -= 258;
				north[2] -= 258;
				
				trans[0] += 3;
				trans[0] += 3;
			}
			
			Vector3d location = new Vector3d( 
					trans[ 0 ], 
					2.5f /* camera height above floor */,
					trans[ 2 ] );
			
			
//			{
//				Vector3d west = new Vector3d( (float)( trans[ 0 ] - north[ 0 ]), 0f, (float)(north [ 2 ] - trans [ 2 ] ) ); 
//				west.scale( 0.6f / west.length() );
//				location.add( west );
//			}
			
//			System.out.println( "pano@ " + location );
		
			return new Pano ( name, location, 
				   (pos.get( 3 ).floatValue()+180),// + 360 - (toNorth * 180 /FastMath.PI ) ) % 360, 
					pos.get( 4 ).floatValue(), 
					pos.get( 5 ).floatValue() );
			
		} catch ( IndexOutOfBoundsException e ) {
			e.printStackTrace();
		} catch ( NoSuchAuthorityCodeException e ) {
			e.printStackTrace();
		} catch ( FactoryException e ) {
			e.printStackTrace();
		} catch ( TransformException e ) {
			e.printStackTrace();
		}
		return null;
	}
	
	public void downloadPanos() {
		
		File indexFile =  new File ( Tweed.toWorkspace( folder ), TO_DOWNLOAD);
		
		try {
			List<String> lines = Files.lines( indexFile.toPath() ).collect( Collectors.toList() );
			
			new Mosaic( 
//					panos.stream().map( p -> p.name ).collect( Collectors.toList() ),
					lines,
					Tweed.toWorkspace( folder ) );
			
			indexFile.renameTo( new File (Tweed.toWorkspace( folder ), DOWNLOADED ) );
			
			calculateOnJmeThread();
			
		} catch ( IOException e ) {
			e.printStackTrace();
		}
		
	}
	
	static final String TO_DOWNLOAD = "todo.list", DOWNLOADED = "done.list";
	
	@Override
	public JComponent getUI() {
		
		ui.removeAll();
		
		ui.setLayout( new ListDownLayout() );
		ui.add(new JLabel(panos.size() +" panoramas"));
		
		if ( folder != null ) {
			File absFolder = new File( Tweed.toWorkspace( folder ), TO_DOWNLOAD );

			if ( absFolder.exists() ) {
				JButton download = new JButton( "download" );
				download.addActionListener( e -> downloadPanos() );
				ui.add( download );
			}
		}
		
		JButton align = new JButton("facade tool");
		align.addActionListener( e -> tweed.setTool(new FacadeTool(tweed)) );
		ui.add( align );
		
		JButton plane = new JButton("plane tool");
		plane.addActionListener( e -> tweed.setTool(new PlaneTool(tweed)) );
		ui.add( plane );
				
		return ui;
	}

	public void selected( Pano p ) {
		
		ui.removeAll();
		ui.setLayout( new ListDownLayout() );
		
		selectedPano = p;
		
		JTextArea name = new JTextArea(  p.orig.getName() );
		name.setLineWrap( true );
		name.setPreferredSize( new Dimension( 600,100) );
		
		JButton recalc = new JButton("reset");
		recalc.addActionListener( new ActionListener() {
			
			@Override
			public void actionPerformed( ActionEvent e ) {
				tweed.enqueue( new Runnable() {
					
					@Override
					public void run() {
						p.set( p.oa1, p.oa2, p.oa3 );
						calculate();
					}});
			}
		} );
		
		JButton depth = new JButton( "depth" );
		depth.addActionListener( new ActionListener() {

			@Override
			public void actionPerformed( ActionEvent e ) {
				Point3d worldPos = new Point3d();
				Vector3d worldNormal = new Vector3d();

				BufferedImage im = p.getRenderPano();
				
				List<float[]> cubes = new ArrayList();
				for ( double a = 0; a < 2 * Math.PI; a += 0.02 )
					for ( double b = -Math.PI / 2; b < Math.PI / 2; b += 0.02 ) {

						Point3d pt = new Point3d( Math.cos( b ) * Math.cos( a ), Math.sin( b ), Math.cos( b ) * Math.sin( a ) );
						pt.scale( 10 );
						pt.add( p.location );

						Color c = new Color ( p.castTo( new float[] { (float) pt.x, (float) pt.y, (float) pt.z }, im, worldPos, worldNormal ) );

						if ( !Double.isNaN( worldPos.x ) )
							cubes.add( new float[] { (float) worldPos.x, (float) worldPos.y, (float) worldPos.z, 
									c.getRed()   / 255f, 
									c.getGreen() / 255f, 
									c.getBlue()  / 255f } );

					}
				addCubes( cubes );
			}
		} );
		
//		JButton render = new JButton("render");
//		render.addActionListener(new ActionListener() {
//			
//			@Override
//			public void actionPerformed(ActionEvent e) {
//				TexGen tg = (TexGen) children.get(0);
//				if (tg != null) {
//					tg.renderTexture( new File(p.orig.getPath()+".projected.png"));
//				}
//			}
//		});
		
		JButton view = new JButton("view");
		view.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				tweed.enqueue(new Runnable() {
					@Override
					public void run() {
						tweed.getCamera().setLocation(p.geom.getWorldTranslation());
						tweed.gainFocus();
					}
				} );
			}
		});
		
		JButton background = new JButton("set background");
		background.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				
				if (TweedSettings.settings.SSAO) {
					JOptionPane.showMessageDialog( tweed.frame.frame, "Disable SSAO (in the settings menu), then restart." );
					return;
				}
				
				
				tweed.enqueue(new Runnable() {
					@Override
					public void run() {
						
						System.out.println("rendering from "+p.orig.getPath() );
						
						int wS = tweed.getCamera().getWidth(),
							hS = tweed.getCamera().getHeight(),
							wC = 1024,
							hC = 1024;//
						
						BufferedImage target = new BufferedImage( wC, hC, BufferedImage.TYPE_3BYTE_BGR);
						p.ensurePano();
						
						for (int x = 0; x < wC; x++)
							for (int y = 0; y < hC; y++) {
								com.jme3.math.Vector3f loc = tweed.getCamera().getWorldCoordinates(new Vector2f(x * wS / wC, y * hS / hC), 1);
								target.setRGB(x, hC-y-1, 
										p.castTo ( new float[] {loc.x, loc.y, loc.z}, p.panoMedium, null, null ) );
							}
						
//						Graphics2D g2 = (Graphics2D) target.getGraphics();
//						g2.setColor (Color.blue);
//						g2.drawLine( target.getWidth()/2, 0, target.getWidth()/2, target.getHeight() );
//						g2.dispose();
						
						tweed.setBackground(target);
					}
				} );
			}
		});
		
		ui.add(name);
		ui.add(view);
//		ui.add(recalc);
		ui.add(background);
//		ui.add(depth);
		
		tweed.frame.setGenUI( ui );
		
		ui.revalidate();
		ui.repaint();
	}

	private void addCubes (List<float[]> cubes) {
		tweed.enqueue( new Runnable() {
			public void run() {
				
				Mesh mesh = new Mesh();
				mesh.setMode( Mode.Points );
				
				Vector3f[] verts = new Vector3f[cubes.size()];
				Vector4f[] cols  = new Vector4f[cubes.size()];
				
				for (int i = 0; i < cubes.size(); i++) {
					verts[ i ] = new com.jme3.math.Vector3f( cubes.get( i )[ 0 ], cubes.get( i )[ 1 ], cubes.get( i )[ 2 ] );
					cols[i] = new Vector4f( cubes.get( i )[ 3 ], cubes.get( i )[ 4 ], cubes.get( i )[ 5 ], 1 );
				}
				
				mesh.setBuffer( VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer( verts ) );
				mesh.setBuffer( VertexBuffer.Type.Color   , 4, BufferUtils.createFloatBuffer( cols  ) );
				
				Material mat1 = new Material( tweed.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md" );
				mat1.setBoolean( "VertexColor", true );
				Geometry depth = new Geometry( "depth", mesh );
				depth.setMaterial( mat1 );
				
				depth.updateModelBound();
				depth.updateGeometricState();
				
				
				tweed.frame.addGen( new JmeGen( "depth", tweed, depth ), true );
			}
		} );
	}

	public List<Pano> getPanos(){
		return panos;
	}

	@Override
	public void dumpObj(ObjDump dump) {
		Jme3z.dump( dump, gNode, 0 );
	}
	
	
	@Override
	public void onLoad( Tweed tweed ) {
		super.onLoad( tweed );
		panos = new ArrayList();
		planes = new ArrayList();
		ui = new JPanel();
	}
}
