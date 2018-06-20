package org.	twak.viewTrace.facades;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.vecmath.Matrix4d;
import javax.vecmath.Point2d;
import javax.vecmath.Point3d;
import javax.vecmath.Vector2d;
import javax.vecmath.Vector3d;

import org.twak.camp.Output;
import org.twak.camp.Output.Face;
import org.twak.siteplan.jme.Jme3z;
import org.twak.siteplan.jme.MeshBuilder;
import org.twak.tweed.ClickMe;
import org.twak.tweed.Tweed;
import org.twak.tweed.gen.Pointz;
import org.twak.tweed.gen.WindowGen;
import org.twak.tweed.gen.WindowGen.Window;
import org.twak.tweed.gen.skel.AppStore;
import org.twak.tweed.gen.skel.FCircle;
import org.twak.tweed.gen.skel.MiniRoof;
import org.twak.tweed.gen.skel.WallTag;
import org.twak.utils.Filez;
import org.twak.utils.Mathz;
import org.twak.utils.Pair;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.LoopL;
import org.twak.utils.collections.Loopable;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.geom.Line3d;
import org.twak.utils.geom.LinearForm3D;
import org.twak.viewTrace.facades.GreebleSkel.QuadF;
import org.twak.viewTrace.facades.Grid.Griddable;
import org.twak.viewTrace.facades.MiniFacade.Feature;
import org.twak.viewTrace.facades.Tube.CrossGen;
import org.twak.viewTrace.franken.App.TextureUVs;
import org.twak.viewTrace.franken.FacadeTexApp;
import org.twak.viewTrace.franken.PanesLabelApp;
import org.twak.viewTrace.franken.PanesTexApp;
import org.twak.viewTrace.franken.RoofTexApp;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture.WrapMode;

public class GreebleGrid {

	MMeshBuilderCache mbs;
	
	private Tweed tweed;


	public GreebleGrid(Tweed tweed, MMeshBuilderCache mbs) {
		this.tweed = tweed;
		this.mbs = mbs;
	}
	
	public void attachAll( Node node, List<Face> chain, Output output, ClickMe clickMe ) {
		for ( String mName : mbs.cache.keySet() )
			for (float[] mCol : mbs.cache.get( mName ).keySet() )		
				node.attachChild( mb2Geom( output, chain, mName, mCol, node, clickMe, mbs.get( mName, mCol ) ) );
		
		for (String mName : mbs.textures.cache.keySet())
			for (String texture : mbs.textures.cache.get( mName ).keySet() ) 
				node.attachChild( mb2Tex( output, chain, mName, texture, node, clickMe, mbs.getTexture( mName, texture ) ) );
		
		// texture atlas.dump
	}
	
	private Geometry mb2Tex( Output output, List<Face> chain, String name, 
			String texture, Node node, ClickMe clickMe, MatMeshBuilder mmb ) {
		Geometry geom;
		{
			MatMeshBuilder builder =  mbs.getTexture( name, texture );
			
			geom = new Geometry( "material_" +texture, builder.getMesh() );
			geom.setUserData( Jme3z.MAT_KEY, name );
			
			/*
			 * if  (textureAtlas != null) {
				xy = texutreAtlas.insert (texture)
				geom.getMesh().getBuffer( Type.TexCoord ); / 16 + (xy) / 16
				set edge clamp
				set material texture to one given by atlas
				
				after all call dump on texture atlas. 

				- mip-mapping needs to be disabled to avoid bleeding.
				
				
			}
			*/
			
			Material mat = new Material( tweed.getAssetManager(), "Common/MatDefs/Light/Lighting.j3md" );
			
			if (new File( Tweed.DATA +"/" +texture ).exists()) {
				Texture t = tweed.getAssetManager().loadTexture( texture );
				t.setWrap( WrapMode.Repeat );
//				t.setWrap( WrapMode.EdgeClamp );
				
				mat.setTexture( "DiffuseMap", t );
				mat.setColor( "Diffuse", ColorRGBA.White );
				mat.setBoolean( "UseMaterialColors", true );
				
				String ext = Filez.getExtn( texture );
				
				String normal   = Filez.stripExtn( texture )+"_norm." + ext,
						specular = Filez.stripExtn( texture )+"_spec." + ext;
				
				if (new File( Tweed.DATA +"/" +normal ).exists()) {
					Texture n = tweed.getAssetManager().loadTexture( normal );
					n.setWrap( WrapMode.Repeat );
					mat.setTexture( "NormalMap", n );
				}
				
			mat.setColor( "Ambient", ColorRGBA.Gray );
				
				if (new File( Tweed.DATA +"/" +specular ).exists()) {
					Texture s = tweed.getAssetManager().loadTexture( specular );
//					mat.setVector4( "Specular", ColorRGBA.Black.toVector4f() );
					s.setWrap( WrapMode.Repeat );
					mat.setFloat( "Shininess", 50 );
					mat.setTexture( "SpecularMap", s );
				}
				mat.setColor( "Specular", ColorRGBA.White );
//			else
//				mat.setColor( "Specular", ColorRGBA.Red );
			}
			else
			{
				System.out.println( this.getClass().getSimpleName() + " can't find "+ tweed.SCRATCH+texture );
				mat.setColor( "Diffuse", ColorRGBA.Red );
				mat.setBoolean( "UseMaterialColors", true );
			}
//			mat.setColor( "Ambient", ColorRGBA.White );

			geom.setUserData( GreebleSkel.Appearance, new Object[] { mmb.app } );
			
			geom.setMaterial( mat );
			geom.updateGeometricState();
			geom.updateModelBound();

			if ( chain != null )
				geom.setUserData( ClickMe.class.getSimpleName(), new Object[] { clickMe } );

		}
		return geom;
	}

	private Geometry mb2Geom( Output output, List<Face> chain, String name, 
			float[] col, Node node, ClickMe clickMe, MatMeshBuilder mmb ) {
		Geometry geom;
		{
			geom = new Geometry( "material_" + col[ 0 ] + "_" + col[ 1 ] + "_" + col[ 2 ], mbs.get( name, col ).getMesh() );
			geom.setUserData( Jme3z.MAT_KEY, name );
			
			Material mat = new Material( tweed.getAssetManager(), "Common/MatDefs/Light/Lighting.j3md" );
			mat.setColor( "Diffuse", new ColorRGBA( col[ 0 ], col[ 1 ], col[ 2 ], col[ 3 ] ) );
			mat.setColor( "Ambient", new ColorRGBA( col[ 0 ] * 0.5f, col[ 1 ] * 0.5f, col[ 2 ] * 0.5f, col[ 3 ] ) );

			mat.setBoolean( "UseMaterialColors", true );

			geom.setMaterial( mat );
			geom.updateGeometricState();
			geom.updateModelBound();
			
			geom.setUserData( GreebleSkel.Appearance, new Object[] { mmb.app } );

			if ( chain != null )
				geom.setUserData( ClickMe.class.getSimpleName(), new Object[] { clickMe } );

		}
		return geom;
	}
	
	
	public void createWindow( DRectangle winPanel, Matrix4d to3d, 
			MeshBuilder wall, 
			MeshBuilder window, 
			MeshBuilder glass, 
			double depth,
			float sillDepth, float sillHeight,
			float corniceHeight,
			double panelWidth, double panelHeight ) {
		
		Point2d[] pts = winPanel.points();
		
		Point3d[] ptt = new Point3d[4];
		
		for (int i = 0; i < 4; i++) {
			ptt[i] = Pointz.to3( pts[i] );
			to3d.transform( ptt[i] ); 
		}
		
		Vector3d along = new Vector3d(ptt[3]);
		along.sub(ptt[0]);
		along.normalize();
		
		Vector3d up = new Vector3d(ptt[1]);
		up.sub(ptt[0]);
		up.normalize();

		Vector3d out = new Vector3d();
		out.cross( along, up );
		out.scale(-1/out.length());
		
		Vector3d loc = new Vector3d();
		loc.cross( along, up );
		loc.scale ( -depth / loc.length() );
		loc.add(ptt[0]);
		
		WindowGen.createWindow( window, glass, new Window( Jme3z.to ( loc ), Jme3z.to(along), Jme3z.to(up), 
				winPanel.width, winPanel.height, 0.0, panelWidth, panelHeight ) ); 
		
		Vector3f u = Jme3z.to(up), o = Jme3z.to( out );
		
		if (wall != null)
			wall.addInsideRect( Jme3z.to ( ptt[0] ), o, Jme3z.to(along), u,  
				 (float)depth, (float)winPanel.width,(float) winPanel.height, null, MatMeshBuilder.ALL_BUT_FRONT  );
		
		if (sillDepth > 0 && sillHeight > 0)
			window.addCube( Jme3z.to ( ptt[0] ).add( u.mult( -sillHeight + 0.01f ) ).add( o.mult( -sillDepth) ),
				Jme3z.to(out), Jme3z.to(along), Jme3z.to(up),
				(float)depth + sillDepth, (float)winPanel.width,(float) sillHeight  );
		
		if (corniceHeight > 0) 
			moulding( to3d, new DRectangle(winPanel.x, winPanel.getMaxY(), winPanel.width, corniceHeight), wall );
	}

	
	protected void createDormerWindow( 
			AppStore appCache,
			MiniRoof roof,
			QuadF l, 
			MeshBuilder window, 
			MeshBuilder glass, 
			float sillDepth,
			float sillHeight,
			float corniceHeight, 
			double panelWidth, double panelHeight ) {
		
		Vector3d along = new Vector3d(l.corners[3]);
		along.sub(l.corners[0]);
		along.normalize();
		
		Vector3d up = new Vector3d(0,1,0);
		
		Vector3d out = new Vector3d();
		out.cross( along, up );
		out.scale( 1 / out.length());
		
		Line3d lout;
		{
			Point3d away = new Point3d( l.corners[ 0 ] );
			away.add( out );
			lout = new Line3d( new Point3d( l.corners[ 0 ] ), away );
		}
		
		Vector3d loc = new Vector3d(l.found[0]);

		if ( lout.findPPram( l.found[ 0 ] ) < lout.findPPram( l.found[ 1 ] ) ) { // outwards going wall...
			loc = new Vector3d( up );
			loc.scale( -l.original.height );
			loc.add( l.found[ 1 ] );
		}
		
		{
			Vector3d avoidRoof = new Vector3d(out);
			avoidRoof.scale( 0.09 );
			loc.add( avoidRoof );
		}
		
		Point3d deepest = Arrays.stream( l.found )
		.map ( p -> new Pair<Point3d, Double> (p,  lout.findPPram( p )) )
		.max( (a,b ) -> b.second().compareTo( a.second() ) ).get().first();
		
		double depth = lout.closestPointOn( deepest, false ).distance( lout.closestPointOn( new Point3d( loc ), false ) ); 

		Matrix4d to3d;
		
		double outset = 0.1;
		
		{
			Matrix4d to2dXY = new Matrix4d();
			
			to2dXY.setRow( 2, up.x, up.y, up.z, 0);
			to2dXY.setRow( 1, -out.x, -out.y, -out.z, 0);
			to2dXY.setRow( 0, along.x, along.y, along.z, 0);
			
			Vector3d p = new Vector3d (out);
			p.scale( outset / p.length() ); /* push frame outwards */
			p.add(loc);
			
			to2dXY.m33 = 1;
			to2dXY.transform( p );
			
			to2dXY.m03 = -p.x;
			to2dXY.m13 = -p.y;
			to2dXY.m23 = -p.z;
			
			to3d = new Matrix4d(to2dXY);
			to3d.invert();
		}
		
		FRect w = new FRect ( l.original );
		
		
		PanesLabelApp wa = appCache.get(PanesLabelApp.class, w);
		
		// find roof locations / uv coordinates for roof
		if (wa != null)
		{
			wa.coveringRoof = new Loop<Point2d>();

			Point2d p = new Point2d(loc.x, loc.z), d = new Point2d();
			
			d.scaleAdd( -depth, new Vector2d ( out.x, out.z), p );
			p.scaleAdd(outset, new Vector2d ( out.x, out.z ) , p);
			
			wa.coveringRoof.append( new Point2d( p ) );
			wa.coveringRoof.append( new Point2d( d ) );
			
			d.scaleAdd(w.width, new Vector2d ( along.x, along.z ), d);
			wa.coveringRoof.append( new Point2d( d ) );
			
			p.scaleAdd(w.width, new Vector2d ( along.x, along.z ), p);
			wa.coveringRoof.append( new Point2d( p ) );
			
		}
		
		
		w.x = 0;
		w.y = 0;
		
		if (tweed != null)
		if (wa == null || wa.texture == null)
			WindowGen.createWindow( window, glass, new Window( Jme3z.to ( loc ), Jme3z.to(along), Jme3z.to(up), 
					l.original.width, l.original.height, depth, panelWidth, panelHeight ) );
		else if (wa.panes == null) {
			createInnie( w, null, to3d, window, 0.2f, 0, MeshBuilder.NO_FRONT_OR_BACK ); 
			mbs.getTexture( "texture_"+wa.texture+"_window_"+w.hashCode(), wa.texture, w ).add( w, ZERO_ONE_UVS, to3d, -0.2 );
		} else {
			createInnie( w, null, to3d, mbs.GRAY, -depth + outset - 0.5, -depth + outset - 0.5, 
					new boolean[] {true, true /*right*/, false, true /*left*/, false, false } ); // walls around window

			
			if (wa.coveringRoof != null) {
				
				double height = loc.y + w.height;
				
				LoopL<Point3d> rp = wa.coveringRoof.singleton().new Map<Point3d>() {

					@Override
					public Point3d map( Loopable<Point2d> input ) {
						return Pointz.to3( input.get(), height );
					}
				}.run();
				
				RoofTexApp rta = appCache.get(RoofTexApp.class, roof);
				
				LoopL<Point2d> uvs = wa.coveringRoof.singleton().new Map<Point2d>() {
					@Override
					public Point2d map( Loopable<Point2d> input ) {
						
						if (rta.textureRect == null)
							return input.get();
						else
							return rta.textureRect.normalize( input.get() );
					}
				}.run();
				
				MeshBuilder roofTexture = rta.texture == null ? window : mbs.getTexture( "roof_" + rta.texture, rta.texture, roof );
				
				roofTexture.add( rp, rta.texture == null ?  null : uvs, true );
			
			}
			else {
				createInnie( w, null, to3d, window, -depth + outset - 0.5, -depth + outset - 0.5, 
					new boolean[] {false, false, true, false, false, false } ); // roof over window
			}
			
			createWindowFromPanes (wa.panes, w,w, to3d, 
					mbs.getTexture( "texture_"+wa.texture+"_window_"+w.hashCode(), wa.texture, w ), 
					0.1, 0 );
		}
	}
	

	public void moulding( Matrix4d to3d, DRectangle rect, MeshBuilder mb ) {
		
		double hh = rect.height/2;
		
		Point3d start = new Point3d (rect.x, 0, rect.y+hh), end = new Point3d (rect.getMaxX(), 0, rect.y+hh);
		
		to3d.transform( start );
		to3d.transform( end   );
		
		Line3d line= new Line3d(start, end);
		
		Vector3d dir = line.dir();
		dir.normalize();
		Vector3d nDir = new Vector3d( dir );
		nDir.scale( -1 );
		
		LinearForm3D left = new LinearForm3D( nDir, start ), right = new LinearForm3D( dir, end);
		
		LinearForm3D wall = new LinearForm3D( to3d.m01,to3d.m11,to3d.m21 );
		wall.findD(start);
		
		Tube.tube( mb, Collections.singleton( left ), Collections.singleton( right ), 
				line, wall, wall, 1, new CrossGen() {
					
					@Override
					public List<Point2d> gen( Vector2d down, Vector2d up ) {
						
						Vector2d d = new Vector2d(down);
						d.normalize();
						
						Vector2d dP = new Vector2d(d.y, -d.x );
						
						List<Point2d> out = new ArrayList();
						
						for (double[] coords : new double[][] {
							{1.00, 0.00},
							{1.00, 0.05},
							{0.66, 0.05},
							{0.66, 0.10},
							{0.33, 0.10},
							{0.33, 0.17},
							{0.00, 0.17},
							{0.00, 0.00},
							} ) {
								Point2d tmp = new Point2d(d);
								tmp.scale (coords[0] * rect.height - hh);
								Point2d tmp2 = new Point2d( dP );
								tmp2.scale (coords[1]);
								tmp.add(tmp2);
							
								out.add(tmp);
						}
						
						return out;
					}
				} );
		
	}

	protected static Vector3f[] findWorldBox( DRectangle door, Matrix4d to3d, double depth ) {
		
		Point2d[] pts = door.points();
		
		Point3d[] ptt = new Point3d[4];
		
		for (int i = 0; i < 4; i++) {
			ptt[i] = Pointz.to3( pts[i] );
			to3d.transform( ptt[i] ); 
		}
		
		Vector3d along = new Vector3d(ptt[3]);
		along.sub(ptt[0]);
		along.normalize();
		
		Vector3d up = new Vector3d(ptt[1]);
		up.sub(ptt[0]);
		up.normalize();
		
		Vector3d out = new Vector3d();
		out.cross( along, up );
		out.normalize();
		
		Vector3d loc = new Vector3d();
		loc.cross( along, up );
		loc.scale ( -depth / loc.length() );
		loc.add(ptt[0]);
		
		Vector3f lo = Jme3z.to ( loc ),
				 ou = Jme3z.to ( out ), 
				 al = Jme3z.to ( along ), 
				 u  = Jme3z.to ( up ),
				 p  = Jme3z.to( ptt[0] );
		
		return new Vector3f[] { lo, ou, al, u, p };
	}
	
	
	protected static void createInnie( DRectangle rect, DRectangle uvs, Matrix4d to3d, MeshBuilder mat, 
			double depth, double atDepth, boolean[] hasBack ) {
		
		Vector3f[] jpts = findWorldBox( rect, to3d, depth );
		
		Vector3f lo = jpts[0],
		 		 ou = jpts[1], 
				 al = jpts[2], 
				 u  = jpts[3],
				 p  = jpts[4];
		
		Vector3f depthOffset = ou.mult( (float) atDepth / ou.length() );
		p = p.add( depthOffset );
		
		mat.addInsideRect( p, ou, al, u, -(float)depth, (float)rect.width, (float) rect.height, 
				uvs == null ? null :
				new float[][] { 
			{ (float) uvs.x, (float)uvs.y},
			{ (float) uvs.getMaxX(), (float) uvs.getMaxY() } }, hasBack  );
	}
	
	public static void createWindowFromPanes( List<DRectangle> panes, DRectangle bounds, DRectangle uvs, Matrix4d to3d, 
			MatMeshBuilder window, double paneDepth, double frameDepth ) {

//		Grid g = new Grid( .010, allGeom.x, allGeom.getMaxX(), allGeom.y, allGeom.getMaxY() );

		if (panes.size() == 0) {
			double delta = 0.02; 
			panes.add( new DRectangle ( delta / bounds.width, delta / bounds.height, 
					(bounds.width - delta *2)/ bounds.width, (bounds.height - delta * 2)/ bounds.height ) );
		}
		
		Grid grid = new Grid( 0.01, bounds.x, bounds.getMaxX(), bounds.y, bounds.getMaxY() );
		
		for (DRectangle pane : panes) {
			grid.insert( bounds.transform( pane ), new Griddable() {
				@Override
				public void instance( DRectangle rect ) {
					createInnie( rect, 
							uvs.normalize( rect ), to3d, window, (paneDepth - frameDepth), -frameDepth, MeshBuilder.ALL_BUT_FRONT );
				}
			});
		}
		
		grid.instance( new Griddable() {
			@Override
			public void instance( DRectangle rect ) {
				window.add( rect, uvs.normalize( rect ), to3d, -frameDepth );
			}
		} );
		
	}
	
	protected void createDoor( DRectangle door, Matrix4d to3d, MeshBuilder woof, MeshBuilder wood, double depth ) {
		
		Vector3f[] jpts = findWorldBox( door, to3d, depth );
		
		Vector3f lo = jpts[0],
				 ou = jpts[1], 
				 al = jpts[2], 
				 u  = jpts[3],
				 p  = jpts[4];
		
		woof.addInsideRect( p, ou, al, u, -(float)depth, (float)door.width, (float) door.height, null, MatMeshBuilder.ALL_BUT_FRONT  );
		
		float height = (float)door.height;
		float width = (float)door.width;
		
		wood.addCube( lo, u, al, ou, (float) height, (float) width, 0.1f );
		
		float fWidth = 0.05f;
		
		// bottom, top
		wood.addCube( lo.add(u.mult( ( height - fWidth))), u, al, ou, fWidth, width, 0.15f );
		
		// left, right
		wood.addCube( lo,                            u, al, ou, height, fWidth, 0.15f );
		wood.addCube( lo.add(al.mult(width-fWidth)), u, al, ou, height, fWidth, 0.15f );
	}

	protected void createBalcony( DRectangle balc, Matrix4d to3d, 
			MeshBuilder mat, double _depth ) {
		
		Point2d[] pts = balc.points();
		
		Point3d[] ptt = new Point3d[4];
		
		
		Vector3f[] ptf = new Vector3f[4];
		
		for (int i = 0; i < 4; i++) {
			ptt[i] = Pointz.to3( pts[i] );
			to3d.transform( ptt[i] ); 
			ptf[i] = Jme3z.to(ptt[i]);
		}
		
		Vector3f along = ptf[3].subtract( ptf[0] );
		along.normalizeLocal();
		
		Vector3f up = ptf[1].subtract(ptf[0]);
		up.normalizeLocal();
		Vector3f out = along.cross( up );
		Vector3f loc = ptf[0];

		
		float bg = 0.04f, sm = 0.01f, height  = balc.heightF(), 
				depth = (float) _depth, width = balc.widthF(),
				spacing = 0.1f, bgsm = (bg - sm) / 2;
		
		// floor
		mat.addCube(loc, up, along, out, bg, width, (float) depth );
		
		// top railings
		
		mbs.GRAY.addCube(loc.add(up.mult( height )), up, along, out, bg, bg * 2, depth );
		mbs.GRAY.addCube(loc.add(up.mult( height ).add(along.mult(width-2*bg))), up, along, out, bg, bg*2, depth );
		mbs.GRAY.addCube( loc.add( up.mult( height ).add( out.mult( depth - bg ) ) ), up, along, out, bg, width, bg*2 );
		
		int count = (int)(depth/spacing);
		
		// side bars
		Vector3f l2 = loc.add(up.mult(0.01f));
		for (int c = 0; c< count; c++) {
			
			
			mat.addCube(l2.add(out.mult(c * spacing)).add(along.mult(bgsm)) , up, along, out, height , sm, sm );
			mat.addCube(l2.add(out.mult(c * spacing)).add(along.mult(width - sm - bgsm)) , up, along, out, height, sm, sm );
		}
		
		count = (int) ( width / spacing);
		spacing = (width - sm -2*bgsm) / count;
				
		// front bars
		for (int c = 0; c< count+1; c++) {
			
			mat.addCube(l2.add(out.mult(depth - sm-bgsm)).add(along.mult(bgsm + spacing * c)) , up, along, out, height, sm, sm);
			
		}
	}
	
	
	protected void buildGrid( AppStore ac, DRectangle all, Matrix4d to3d, 
			FeatureGenerator filteredFeatuers, MiniFacade mf, MatMeshBuilder wallColorMat, WallTag wallTag ) {

		Grid g = new Grid( .010, all.x, all.getMaxX(), all.y, all.getMaxY() );

		if ( mf != null ) {

			wallColorMat.app = mf;
			
			for ( FRect w : filteredFeatuers.get( Feature.WINDOW ) ) {

				if ( all.contains( w ) )
					g.insert( w, new Griddable() {
						@Override
						public void instance( DRectangle rect ) {

//							MatMeshBuilder wood  = mbs.get( mbs.WOOD .name+w.hashCode(), mbs.wood,  w );
//							MatMeshBuilder glass = mbs.get( mbs.GLASS.name+w.hashCode(), mbs.glass, w );
							
							createWindow( rect, to3d, 
									wallColorMat, mbs.WOOD, mbs.GLASS, // wood, glass,
									wallTag.windowDepth, 
									(float) wallTag.sillDepth, 
									(float) w.attachedHeight.get(Feature.SILL).d, 
									(float) w.attachedHeight.get(Feature.CORNICE).d, 0.6, 0.9 );
						}
					} );
				
				double bHeight = w.attachedHeight.get(Feature.BALCONY).d;
				if (bHeight > 0) {
					
					DRectangle balcon = new DRectangle();
					balcon.setFrom (w);
					balcon.grow (0.2);
					balcon.height = bHeight;
					
					createBalcony( balcon, to3d, mbs.BALCONY, wallTag.balconyDepth );
				}
				
			}

			for ( FRect s_ : filteredFeatuers.get( Feature.SHOP ) ) {
				
				FRect s = new FRect(s_);
				
				DRectangle rect = all.intersect( s );
				
				if (rect != null) {
				s.setFrom(  rect );
				
					g.insert( s, new Griddable() {
						@Override
						public void instance( DRectangle rect ) {

							MatMeshBuilder wood  = mbs.get( mbs.WOOD .name+s.hashCode(), mbs.wood,  s );
							MatMeshBuilder glass = mbs.get( mbs.GLASS.name+s.hashCode(), mbs.glass, s );
							
							createWindow( rect, to3d, wallColorMat, wood, glass, 
									wallTag.windowDepth, 
									(float) wallTag.sillDepth, 
									(float) s.attachedHeight.get(Feature.SILL).d, 
									(float) s.attachedHeight.get(Feature.CORNICE).d,
									1.5, 2 );
						}
					} );
				}
			}
			for ( DRectangle d : filteredFeatuers.get( Feature.DOOR ) ) {
				if ( all.contains( d ) )
					g.insert( d, new Griddable() {
						@Override
						public void instance( DRectangle rect ) {
							createDoor( rect, to3d, wallColorMat, mbs.get( "wood", new float[] {0,0,0.3f, 1} ), wallTag.doorDepth );
						}
					} );
			}

			for ( DRectangle b : filteredFeatuers.get( Feature.BALCONY ) ) {
				if ( all.contains( b ) )
					g.insert( b, new Griddable() {
						@Override
						public void instance( DRectangle rect ) {
							createBalcony( rect, to3d, mbs.BALCONY, wallTag.balconyDepth );
						}

						@Override
						public boolean noneBehind() {
							return true;
						}
					} );
			}

			for ( DRectangle b : filteredFeatuers.get( Feature.MOULDING ) ) {
				if ( all.contains( b ) )
					g.insert( b, new Griddable() {
						@Override
						public void instance( DRectangle rect ) {
							moulding( to3d, rect, mbs.MOULDING );
						}
					} );
			}
		}

		g.instance( new Griddable() {
			@Override
			public void instance( DRectangle rect ) {
				wallColorMat.add( rect, to3d );
			}
		} );
	}

	final static DRectangle ZERO_ONE_UVS = new DRectangle( 0, 0, 1, 1 );
	
	protected void textureGrid(AppStore ac, DRectangle allGeom, DRectangle allUV, Matrix4d to3d, FeatureGenerator filteredFeatures, MiniFacade mf ) {

		FacadeTexApp fa = ac.get(FacadeTexApp.class, mf);
		
		if ( mf != null && fa.texture != null ) {
			
			Grid g = new Grid( .010, allGeom.x, allGeom.getMaxX(), allGeom.y, allGeom.getMaxY() );
			MatMeshBuilder mmb = mbs.getTexture( "texture_"+fa.texture , fa.texture, mf );

			for ( FRect w : filteredFeatures.getRects( Feature.WINDOW, Feature.SHOP ) ) {

				
				if ( allGeom.contains( w ) ) {
					
					PanesLabelApp pa = ac.get(PanesLabelApp.class, w);
				
					g.insert( w, new Griddable() {
						@Override
						public void instance( DRectangle rect ) {
							if (pa == null || pa.texture == null) // no texture info
								createInnie( rect, allUV.normalize( rect ), to3d, 
										mbs.getTexture( "texture_"+fa.texture+"_window_"+w.hashCode(), 
												fa.texture, w ), 0.2f, 0, MeshBuilder.ALL_BUT_FRONT );
							else if (pa.panes == null) { // just coarse facade
								createInnie( rect, allUV.normalize( rect ), to3d, mmb, 0.2f, 0, MeshBuilder.NO_FRONT_OR_BACK ); 
								mbs.getTexture( "texture_"+pa.texture+"_window_"+w.hashCode(), pa.texture, w ).add( rect, ZERO_ONE_UVS, to3d, -0.2 );
							
							} else if (pa.textureUVs == TextureUVs.Zero_One){ // labels
								
								createInnie( rect, allUV.normalize( rect ), to3d, mmb, 0.2f, 0, MeshBuilder.NO_FRONT_OR_BACK ); 
								createWindowFromPanes (pa.panes, rect, rect, to3d,
										mbs.getTexture( "texture_"+pa.texture+"_window_"+w.hashCode(), pa.texture, w ),
										0.2, 0.17 );
							}
							else { // textures
								
								DRectangle uvs = allUV.normalize( rect );
								createInnie( rect, uvs, to3d, mmb, 0.2f, 0, MeshBuilder.NO_FRONT_OR_BACK );
								createWindowFromPanes (pa.panes, rect, allUV, to3d,
										mbs.getTexture( "texture_"+fa.texture+"_window_"+w.hashCode() , fa.texture, w ),
										0.2, 0.17 );
							}
						}
					} );
				}
			}
			
			for ( FRect w : filteredFeatures.get( Feature.DOOR ) ) {
				
				if ( allGeom.contains( w ) ) {
					
					PanesLabelApp pa = ac.get(PanesLabelApp.class, w);
					
					g.insert( w, new Griddable() {
						@Override
						public void instance( DRectangle rect ) {
							if (pa.texture == null)
								createInnie( rect, allUV.normalize( rect ), to3d, mmb, 0.5f, 0, MeshBuilder.ALL_BUT_FRONT );
							else 
								createInnie( rect, ZERO_ONE_UVS, to3d, mbs.getTexture( "texture_"+fa.texture, fa.texture, w ) , 0.3f, 0, MeshBuilder.ALL_BUT_FRONT );
						}
					} );
				}
			}
			
			for ( FRect w : filteredFeatures.getRects( Feature.MOULDING, Feature.SILL ) ) {
				
				if ( allGeom.contains( w ) )
					g.insert( w, new Griddable() {
						@Override
						public void instance( DRectangle rect ) {
							createInnie( rect, allUV.normalize( rect ), to3d, mmb, -0.1f, 0, MeshBuilder.ALL_BUT_FRONT );
						}
					} );
			}
			for ( FRect w : filteredFeatures.getRects( Feature.CORNICE ) ) {
				
				if ( allGeom.contains( w ) )
					g.insert( w, new Griddable() {
						@Override
						public void instance( DRectangle rect ) {
							createInnie( rect, allUV.normalize( rect ), to3d, mmb, -0.1f, 0, MeshBuilder.ALL_BUT_FRONT );
						}
					} );
			}
			
			for ( FRect b : filteredFeatures.get( Feature.BALCONY ) ) {
				if ( allGeom.contains( b ) ) 
					g.insert( b, new Griddable() {
						@Override
						public void instance( DRectangle rect ) {
							PanesTexApp ba = ac.get(PanesTexApp.class, b);
							createBalcony( rect, to3d, mbs.get( "balcony_"+ba.color, ba.color, mf ) , 0.3 );
						}

						@Override
						public boolean noneBehind() {
							return true;
						}
					} );
			}
			
			g.instance( new Griddable() {
				@Override
				public void instance( DRectangle rect ) {
					mmb.add( rect, allUV.normalize(rect), to3d );
				}
			} );
		}
	}
//
//	/**
//	 * Compute uv stretch for non-square component
//	 */
//	public static DRectangle normalizeForInset( FRect w ) {
//		
//		DRectangle r = new DRectangle(w);
//		
//		double scale = Math.max( w.width, w.height );
//		r.height /= scale;
//		r.width  /= scale * 1.18;
//		
//		if (w.height > w.width) {
//			r.x = 0.5 * (w.height - w.width) / w.height + 0.04;
//			r.y=0;
//		}
//		else
//		{
//			r.y = 0.5 * (w.width - w.height) / w.width;
//			r.x=0;
//			
//		}
////		r.y += Math.max (0, 0.5 * (w.width - w.height) / w.width );
//		
////		if (w.height > w.width) 
////			r.width = 0.2;// w.height / w.width;
////		else
////			r.height = w.height / w.width;
//		
//		return r;
//	}

	public void createChimney( Point3d onRoof, MiniRoof mr, FCircle feature, Vector2d along, LinearForm3D pitch, String tex ) {
		
		MatMeshBuilder mat;
		
		boolean uvs;
		
		if (feature.radius < 0.2) { 
			mat = mbs.GRAY;
			uvs = false;
		}
		else if (tex != null) {
			mat = mbs.getTexture( tex, tex, mr);
			uvs = true;
		}
		else {
			mat = mbs.get( "chimney_brown", new float[] { 78f/255, 51f / 255, 31f / 255, 1f }, null  );
			uvs = false;
		}
		
		along = new Vector2d(along);
		along.normalize();

		
		
		Vector2d al = new Vector2d (along), up = new Vector2d( -al.y, al.x ); // up the roof
		

		al.scale (feature.radius / al.length());
		up.scale (feature.radius / up.length());
		
		Point2d[] a = new Point2d[] {new Point2d(),new Point2d(),new Point2d(),new Point2d() } ;
		
		a[0].scaleAdd (  1, al, feature.loc );
		a[0].scaleAdd (  1, up, a[0] );
		a[1].scaleAdd ( -1, al, feature.loc );
		a[1].scaleAdd (  1, up, a[1] );
		a[2].scaleAdd ( -1, al, feature.loc );
		a[2].scaleAdd ( -1, up, a[2] );
		a[3].scaleAdd (  1, al, feature.loc );
		a[3].scaleAdd ( -1, up, a[3] );
		
		double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
		for (Point2d p : a) {
			Point3d pt = pitch.collide( new Point3d(p.x, p.y, 0 ), Mathz.Z_UP );
			min = Math.min (min, pt.z);
			max = Math.max (max, pt.z);
		}

		double height = Math.max (0.5, (max - min) * 2 );
		
		
		Vector3f locJ = new Vector3f( (float) a[1].x, (float) ( min ) , (float) a[1].y),
				upJ    = new Vector3f(0,1,0),
				alongJ = new Vector3f ( (float) along.x, 0, (float) along.y);
		
		mat.addInsideRect (
				locJ, upJ, alongJ, upJ.cross( alongJ ), 
				(float)height, (float) feature.radius * 2, (float) feature.radius * 2 ,
				uvs ? new float[][] {{0.4f,0.4f},{0.6f,0.6f}} : null, MeshBuilder.ALL );
		
//		mat.addCube( new Point3d(a[1].x, min + height, a[1].y), Mathz.Y_UP, new Vector3d (along.x, 0, along.y) ,
//				-height, feature.radius * 2, feature.radius * 2 );

		double step = 0.1;// Math.min ( feature.radius / 2, 0.1 );
		
		if (feature.radius > 2*step) {
		
		Point3d offset = new Point3d(a[1].x , min, a[1].y );
		
		double sl =feature.radius * 2 - step * 2;
		
		Vector3d upp = new Vector3d( -along.y, 0, along.x ),
				 alg = new Vector3d( along.x, 0, along.y );
		
		offset.scaleAdd (-step, upp, offset);
		offset.scaleAdd (step, alg, offset);
		offset.scaleAdd (height + 0.01, Mathz.Y_UP, offset );
		
		
			mbs.GRAY.addCube( offset, Mathz.Y_UP, alg, -0.02, sl, sl );
			
//			offset.scaleAdd (-sl, upp, offset);
//			offset.scaleAdd (sl, alg, offset);
			
//			mat.addCube( offset, Mathz.Y_UP, alg, -step, -sl, -step );
//			mat.addCube( offset, Mathz.Y_UP, alg, -step, -step, -sl );
		}
	}
	
}
