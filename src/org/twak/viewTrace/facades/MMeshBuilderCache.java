package org.twak.viewTrace.facades;

import java.awt.Color;
import java.io.File;

import org.twak.siteplan.jme.Jme3z;
import org.twak.tweed.ClickMe;
import org.twak.tweed.Tweed;
import org.twak.utils.Cach2;
import org.twak.utils.Filez;
import org.twak.utils.ui.Colourz;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture.WrapMode;

public class MMeshBuilderCache extends Cach2<String, float[], MatMeshBuilder> {
	
	public MMeshBuilderCache() {
		super ( (a,b) -> new MatMeshBuilder( (String) a, (float[]) b ) );
	}
	
	public MatMeshBuilder get (String name, Color col, Object onclick ) {
		return get (name, Colourz.toF4( col ), onclick);
	}
	public MatMeshBuilder get (String name, float[] col, Object onclick ) {
		
		if (col == null)
			System.err.println( "warning null colour" );
		
		MatMeshBuilder out = get( name, col );
//		HasApp.get( onclick ).color = Colour.fromF ( col );
		
		if (out.app == null)
			out.app = onclick;
		else if (out.app != onclick) {
			System.err.println("warning: attempt to assign different click-source");
			put(name, col, out = new MatMeshBuilder(name, col) );
			out.app = onclick;
		}
		
		return out;
	}
	public MatMeshBuilder getTexture (String name, String texture, Object onclick ) {
		
		MatMeshBuilder out = textures.get( name, texture );
		
		if (out.app == null)
			out.app = onclick;
		else if (out.app != onclick)
			System.err.println( "warning: attempt to assign different click-source" );
		
		return out;
	}

	public MatMeshBuilder getTexture( String name, String t ) {
		return textures.get( name, t );
	}
	
	public Cach2<String, String, MatMeshBuilder> textures = new Cach2<String, String, MatMeshBuilder>( (a,b ) -> 
		new MatMeshBuilder( (String)a, (String)b ).ensureUVs(true) );
	
	final static float[] 
			glass = new float[] {0.0f, 0.0f, 0.0f, 1},
			wood = new float[] {0.8f, 0.8f, 0.8f, 1 },
			brown = new float[] {119 / 255f, 78/255f, 22/255f, 1 },
			balcony = new float[] {0.2f, 0.2f, 0.2f, 1},
			moudling = new float[] {0.7f, 0.7f, 0.7f, 1},
			error = new float[] { 0.5f, 0.5f, 0.5f, 1.0f },
			gray = new float[] { 0.1f, 0.1f, 0.1f, 1f } ;

	
	public final MatMeshBuilder
		WOOD  =  get( "wood", wood ),
		BROWN  =  get( "brown", brown ),
		GLASS =  get( "glass", glass ),
		ERROR =  get( "error", error),
		MOULDING = get( "brick", moudling ),
		BALCONY =  get( "balcony", balcony),
		GRAY = get( "chimney_black", gray );
	

	public void attachAll( Tweed tweed, Node node, ClickMe clickMe ) {
		for ( String mName : cache.keySet() )
			for (float[] mCol : cache.get( mName ).keySet() )		
				node.attachChild( mb2Geom( tweed, mName, mCol, node, clickMe, get( mName, mCol ) ) );
		
		for (String mName : textures.cache.keySet())
			for (String texture : textures.cache.get( mName ).keySet() ) 
				node.attachChild( mb2Tex( tweed, mName, texture, node, clickMe, getTexture( mName, texture ) ) );
	}
	

	private Geometry mb2Tex( Tweed tweed, String name, String texture, Node node, 
			ClickMe clickMe, MatMeshBuilder mmb ) {
		
		Geometry geom;
		{
			MatMeshBuilder builder =  getTexture( name, texture );
			
			geom = new Geometry( "material_" +texture, builder.getMesh() );
			geom.setUserData( Jme3z.MAT_KEY, name );
			
			Material mat;
			
			mat = buildTextureMaterial( tweed, texture );
//			mat.setColor( "Ambient", ColorRGBA.White );

			geom.setUserData( GreebleSkel.Appearance, new Object[] { mmb.app } );
			
			geom.setMaterial( mat );
			geom.updateGeometricState();
			geom.updateModelBound();

			if ( clickMe != null )
				geom.setUserData( ClickMe.class.getSimpleName(), new Object[] { clickMe } );

		}
		return geom;
	}

	public static Material buildTextureMaterial( Tweed tweed, String texture ) {
		Material mat;
		mat = new Material( tweed.getAssetManager(), "Common/MatDefs/Light/Lighting.j3md" );
		
		if ( new File( Tweed.DATA + "/" + texture ).exists() ) {
			Texture t = tweed.getAssetManager().loadTexture( texture );
			t.setWrap( WrapMode.Repeat );

			mat.setTexture( "DiffuseMap", t );
			mat.setColor( "Diffuse", ColorRGBA.White );
			mat.setBoolean( "UseMaterialColors", true );

			String ext = Filez.getExtn( texture );

			String normal = Filez.stripExtn( texture ) + "_norm." + ext, specular = Filez.stripExtn( texture ) + "_spec." + ext;

			if ( new File( Tweed.DATA + "/" + normal ).exists() ) {
				Texture n = tweed.getAssetManager().loadTexture( normal );
				n.setWrap( WrapMode.Repeat );
				mat.setTexture( "NormalMap", n );
			}

			mat.setColor( "Ambient", ColorRGBA.Gray );

			if ( new File( Tweed.DATA + "/" + specular ).exists() ) {
				Texture s = tweed.getAssetManager().loadTexture( specular );
				s.setWrap( WrapMode.Repeat );
				mat.setFloat( "Shininess", 50 );
				mat.setTexture( "SpecularMap", s );
			}
			mat.setColor( "Specular", ColorRGBA.White );
		}
		else
		{
			System.out.println( " can't find "+ tweed.SCRATCH+texture );
			mat.setColor( "Diffuse", ColorRGBA.Green );
			mat.setColor( "Ambient", ColorRGBA.Red );
			mat.setBoolean( "UseMaterialColors", true );
		}
		return mat;
	}

	private Geometry mb2Geom( Tweed tweed, String name, 
			float[] col, Node node, ClickMe clickMe, MatMeshBuilder mmb ) {
		Geometry geom;
		{
			geom = new Geometry( "material_" + col[ 0 ] + "_" + col[ 1 ] + "_" + col[ 2 ], get( name, col ).getMesh() );
			geom.setUserData( Jme3z.MAT_KEY, name );
			
			Material mat = new Material( tweed.getAssetManager(), "Common/MatDefs/Light/Lighting.j3md" );
			mat.setColor( "Diffuse", new ColorRGBA( col[ 0 ], col[ 1 ], col[ 2 ], col[ 3 ] ) );
			mat.setColor( "Ambient", new ColorRGBA( col[ 0 ] * 0.5f, col[ 1 ] * 0.5f, col[ 2 ] * 0.5f, col[ 3 ] ) );

			mat.setBoolean( "UseMaterialColors", true );

			geom.setMaterial( mat );
			geom.updateGeometricState();
			geom.updateModelBound();
			
			geom.setUserData( GreebleSkel.Appearance, new Object[] { mmb.app } );

			if ( clickMe != null )
				geom.setUserData( ClickMe.class.getSimpleName(), new Object[] { clickMe } );

		}
		return geom;
	}

}
