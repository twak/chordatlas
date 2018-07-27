package org.twak.viewTrace.franken;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.vecmath.Point2d;
import javax.vecmath.Vector2d;
import javax.vecmath.Vector3d;

import org.twak.camp.Output.Face;
import org.twak.tweed.Tweed;
import org.twak.tweed.gen.SuperEdge;
import org.twak.tweed.gen.skel.FCircle;
import org.twak.tweed.gen.skel.MiniRoof;
import org.twak.tweed.gen.skel.RoofGreeble;
import org.twak.tweed.gen.skel.RoofTag;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.Loopable;
import org.twak.utils.collections.Loopz;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.geom.HalfMesh2.HalfEdge;
import org.twak.utils.ui.ColourPicker;
import org.twak.utils.ui.Colourz;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.facades.FRect;
import org.twak.viewTrace.facades.GreebleSkel;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.facades.MiniFacade.Feature;
import org.twak.viewTrace.franken.Pix2Pix.Job;
import org.twak.viewTrace.franken.Pix2Pix.JobResult;


public class RoofTexApp extends App {

	public String coarse;
	public String texture;
	public MiniRoof mr;
	
	private static Color defaultColor = Colourz.to4( GreebleSkel.BLANK_WALL );
	
	public Color color  = defaultColor;
	
	public TextureUVs textureUVs = TextureUVs.Square;
	public DRectangle textureRect;
	
	public RoofTexApp(MiniRoof mr ) {
		
		super( );
		this.mr = mr;
	}

	public RoofTexApp( RoofTexApp ruf ) {
		super( ruf );
		
		this.coarse = ruf.coarse;
		this.texture = ruf.texture;
		this.mr = ruf.mr;
		this.color = ruf.color;
		
		this.textureUVs = ruf.textureUVs;
		this.textureRect = ruf.textureRect;
	}

	@Override
	public App getUp() {
		
		return mr.roofGreebleApp;
	}

	@Override
	public MultiMap<String, App> getDown() {
		
		MultiMap<String, App> out = new MultiMap<>();
		
		out.put( "super", mr.roofSuperApp );
		
		out.putAll( "velux", mr.greebles.valueList().stream().filter( g -> g.f == RoofGreeble.Velux ).
				map( g -> g.veluxTextureApp ).collect(Collectors.toList()) );
		
//		out.putAll( "velux", mr.greebles.valueList().stream().filter( g -> g.f == RoofGreeble.Velux ).
//				map( g -> g.getVeluxTarget().panesLabelApp ).collect(Collectors.toList()) );
		
		return out;	
	}

	@Override
	public App copy() {
		return new RoofTexApp( this );
	}
	
	public String getTexture( RoofTag rt ) {
		
		String out = null;
		
		RoofSuperApp rsa = mr.roofSuperApp;
		
		if ( rsa.appMode == TextureMode.Net && rsa.textures != null) 
			out = rsa.textures.get( rt );
		
		if (out == null) 
			out = texture;
		
		return out;
	}
	
	@Override
	public void computeBatch(Runnable whenDone, List<App> batch) {
		
		NetInfo ni = NetInfo.get(this);
		Pix2Pix p2 = new Pix2Pix( ni );
		
		int resolution = ni.resolution;
		
		BufferedImage label = new BufferedImage( resolution, resolution, BufferedImage.TYPE_3BYTE_BGR );
		Graphics2D gL = (Graphics2D) label.getGraphics();
		
		BufferedImage empty = new BufferedImage( resolution, resolution, BufferedImage.TYPE_3BYTE_BGR );
		Graphics2D gE = (Graphics2D) empty.getGraphics();

		DRectangle drawTo = new DRectangle( 0, 0, resolution, resolution );
		
		for (App a : batch) {

			RoofTexApp rta = (RoofTexApp)a;
			MiniRoof mr = rta.mr;
			
			if (a.appMode != TextureMode.Net) {
				
				if (a.appMode == TextureMode.Parent) {
					rta.texture = mr.roofGreebleApp.greebleTex;
					rta.textureUVs = TextureUVs.Rectangle;
					mr.roofSuperApp.textures = null;
				}
				
				continue;
			}
			
			
			DRectangle bounds = draw (gL, drawTo, mr, false);
			drawEmpty (gE, drawTo, mr, bounds);
			
			p2.addInput( label, empty, null, mr, mr.roofTexApp.styleZ, null );
		}
		
//		addCoarseRoofInputs( batch.stream().map( x -> ((RoofTexApp)x).mr ).
//				collect( Collectors.toList() ), p2, resolution, false );

		p2.submit( new Job( new JobResult() {
			
			@Override
			public void finished( Map<Object, File> results ) {
				try {

					for ( Map.Entry<Object, File> e : results.entrySet() ) {

						MiniRoof mr = ((MiniRoof)e.getKey());
						
						String dest = Pix2Pix.importTexture( e.getValue(), 50, null,  null, 
								new RescaleOp(0.5f, 1.5f, null ), new BufferedImage[3] );

						if ( dest != null ) {
							
							RoofSuperApp rsa = mr.roofSuperApp;
							RoofTexApp rta = mr.roofTexApp;
							
							rsa.textures = null;
							rta.coarse = rta.texture = dest;
							rta.textureUVs = TextureUVs.Rectangle;
							
							rta.splatToSkirt ( mr.superFace.buildingApp.parent.blockApp ); 
							
						}
					}

				} catch ( Throwable e ) {
					e.printStackTrace();
				} finally {
					whenDone.run();
				}
			}
		} ) );
	}

	private void splatToSkirt( BlockApp block ) {

		if ( block.doSkirt ) {
			try {
				
				BufferedImage dest, src = ImageIO.read (new File (Tweed.DATA, coarse));
				
				String out = "scratch/"+ UUID.randomUUID() + ".png";
				
				if ( block.skirtTexture == null ) {
					dest = new BufferedImage( BlockApp.SKIRT_RES, BlockApp.SKIRT_RES, BufferedImage.TYPE_3BYTE_BGR );
				} else {
					dest = ImageIO.read( new File( Tweed.DATA, block.skirtTexture ) );
				}

				Graphics2D g = dest.createGraphics();
				
				DRectangle s = new DRectangle( BlockApp.SKIRT_RES,BlockApp.SKIRT_RES ).transform( block.getSkirt().normalize( textureRect ) );
				
				s.y+=s.height;
				s.height = -s.height;
				
				g.drawImage( src, s.xI(), s.yI(), s.widthI(), s.heightI(), null );
				
				ImageIO.write( dest, "png", new File( Tweed.DATA, out ) );
				block.skirtTexture = out;
				
			} catch ( Throwable th ) {
				th.printStackTrace();
			}
		}
	}
	public static void addCoarseRoofInputs( List<MiniRoof> mrb, Pix2Pix p2, int resolution, boolean greebles ) {
		
		BufferedImage label = new BufferedImage( resolution, resolution, BufferedImage.TYPE_3BYTE_BGR );
		Graphics2D gL = (Graphics2D) label.getGraphics();
		
		BufferedImage empty = new BufferedImage( resolution, resolution, BufferedImage.TYPE_3BYTE_BGR );
		Graphics2D gE = (Graphics2D) empty.getGraphics();

		DRectangle drawTo = new DRectangle( 0, 0, resolution, resolution );
		
		for ( MiniRoof toEdit : mrb ) {

			DRectangle bounds = draw (gL, drawTo, toEdit, greebles);
			drawEmpty (gE, drawTo, toEdit, bounds);
			
			p2.addInput( label, empty, null, toEdit, toEdit.roofTexApp.styleZ, null );
		}
	}
	
	private static DRectangle draw( Graphics2D g, DRectangle drawTo, MiniRoof mr, boolean greebles ) {
		
		DRectangle bounds = new DRectangle(mr.bounds);
		
		if ( bounds.width > bounds.height ) {
			bounds.y -= ( bounds.width - bounds.height ) / 2;
			bounds.height = bounds.width;
		} else {
			bounds.x -= ( bounds.height - bounds.width ) / 2;
			bounds.width = bounds.height;
		}

		bounds.grow( 2 );
		
		RoofTexApp rta =  mr.roofTexApp;
		rta.textureRect = new DRectangle ( bounds );
		
		bounds.y += bounds.height;
		bounds.height = -bounds.height;
		
		List<Polygon> boundary = Loopz.toPolygon (mr.boundary, bounds, drawTo );
		List<Polygon> pitches  = Loopz.toPolygon (mr.pitches , bounds, drawTo );
		List<Polygon> flats    = Loopz.toPolygon (mr.flats   , bounds, drawTo );
		
		g.setColor( Color.black );
		g.fillRect( (int) drawTo.x, (int) drawTo.x, (int) drawTo.width, (int) drawTo.height );
		
		g.setStroke( new BasicStroke( 3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND) );
		
		g.setColor( Color.cyan );
		for (Polygon p : boundary) {
			g.fill( p );
			g.draw( p );
		}
		
		g.setColor( Color.red );
		for (Polygon p : flats) { 
			g.fill( p );
			g.draw( p );
		}
		
		g.setColor( Color.cyan );
		for (Polygon p : pitches)
			g.fill( p );
		
		g.setStroke( new BasicStroke( 3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND) );
		g.setColor( Color.magenta );
		
		g.setColor( Color.magenta );
		for (Polygon p : pitches)
			g.draw( p );
		
		if ( !greebles ) {
			g.setStroke( new BasicStroke( 5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND ) );
			g.setColor( Color.gray );

			for ( Polygon p : flats )
				g.draw( p );
			for ( Polygon p : boundary )
				g.draw( p );
		}
		
		g.setStroke( new BasicStroke( 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND) );
		
		for (Face f : mr.origins.ab.values()) 
			for (FCircle greeble : mr.getGreebles( f ) ) {
				
				double  radiusW = greeble.radius, 
						radiusH = greeble.radius * ( greeble.f.verticalProjection ? 1 : Math.sin ( f.edge.getAngle() ) );
				
				Loop<Point2d> loop = new Loop<>();
				

				Vector3d e3 = f.edge.direction();
				e3.normalize();
				
				Vector2d 
						x = new Vector2d (  e3.x, e3.y), 
						y = new Vector2d ( -e3.y, e3.x );
				
				
				
				loop.append( new Point2d (greeble.loc.x + x.x * radiusW + y.x * radiusH, greeble.loc.y + x.y * radiusW + y.y * radiusH ) );
				loop.append( new Point2d (greeble.loc.x - x.x * radiusW + y.x * radiusH, greeble.loc.y - x.y * radiusW + y.y * radiusH ) );
				loop.append( new Point2d (greeble.loc.x - x.x * radiusW - y.x * radiusH, greeble.loc.y - x.y * radiusW - y.y * radiusH ) );
				loop.append( new Point2d (greeble.loc.x + x.x * radiusW - y.x * radiusH, greeble.loc.y + x.y * radiusW - y.y * radiusH ) );
				
				Polygon p = Loopz.toPolygon (loop.singleton() , bounds, drawTo ).get(0);
				
				g.setColor( greeble.f.colour );
				g.fill(p);
				
			}
		
		
		g.setStroke( new BasicStroke( 3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND) );
		for (HalfEdge e : mr.superFace) {
			MiniFacade mf = ((SuperEdge)e).toEdit; 
			for (FRect f : mf.featureGen.getRects( Feature.WINDOW )) {
				
				PanesLabelApp pla = f.panesLabelApp;
				
				if (pla.coveringRoof != null) {

					g.setColor( Color.cyan );
					Polygon dormer = Loopz.toPolygon (pla.coveringRoof.singleton(), bounds, drawTo ).get(0);
					g.fill( dormer );

					g.setColor( Color.magenta );
					int count = 0;
					for (Loopable<Point2d> ll : pla.coveringRoof.loopableIterator()) {
						
						if (count ++ == 3)
							continue;
						
						Point2d a = drawTo.transform( bounds.normalize( ll.get() ) );
						Point2d b = drawTo.transform( bounds.normalize( ll.getNext().get() ) );
						
						g.drawLine( (int)a.x, (int)a.y, (int)b.x, (int)b.y );
					}
					
//					g.setColor( Color.magenta );
//					g.draw( dormer );
				}
			}
		}
		
		return bounds;
	}
	
	private static void drawEmpty( Graphics2D g, DRectangle drawTo, MiniRoof mr, DRectangle bounds ) {
		
		g.setColor( Color.black );
		g.fillRect( (int) drawTo.x, (int) drawTo.x, (int) drawTo.width, (int) drawTo.height );
		
		List<Polygon> boundary = Loopz.toPolygon (mr.boundary, bounds, drawTo );
		
		g.setColor( Color.blue );
		g.setStroke( new BasicStroke( 5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND) );
		
		for (Polygon p : boundary) {
			g.fill( p );
			g.draw( p );
		}
		
	}
	
	public Enum[] getValidAppModes() {
		return new Enum[] {TextureMode.Off, TextureMode.Net, TextureMode.Bitmap, TextureMode.Parent};
	}
	
	@Override
	public JComponent createUI( Runnable update, SelectedApps selectedApps ) {
		
		JPanel out = new JPanel( new ListDownLayout() );
		if ( appMode == TextureMode.Net ) {
		} else if ( appMode == TextureMode.Off ) {
			JButton col = new JButton( "color" );

			col.addActionListener( e -> new ColourPicker( null, color ) {
				@Override
				public void picked( Color color ) {

					for ( App a : selectedApps ) {
						( (RoofTexApp) a ).color = color;
						( (RoofTexApp) a ).texture = null;
					}

					update.run();
				}
			} );

			out.add( col );
		}
		return out;
	}
}
