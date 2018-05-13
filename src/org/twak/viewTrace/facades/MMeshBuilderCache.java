package org.twak.viewTrace.facades;

import java.awt.Color;

import org.twak.utils.Cach2;
import org.twak.utils.ui.Colourz;

public class MMeshBuilderCache extends Cach2<String, float[], MatMeshBuilder> {
	
	public MMeshBuilderCache() {
		super ( (a,b) -> new MatMeshBuilder( (String) a, (float[]) b ) );
	}
	
	public MatMeshBuilder get (String name, Color col, HasApp onclick ) {
		return get (name, Colourz.toF4( col ), onclick);
	}
	public MatMeshBuilder get (String name, float[] col, HasApp onclick ) {
		
		if (col == null)
			System.err.println( "warning null colour" );
		
		MatMeshBuilder out = get( name, col );
//		HasApp.get( onclick ).color = Colour.fromF ( col );
		
		if (out.app == null)
			out.app = onclick;
		else if (out.app != onclick)
			System.err.println( "warning: attempt to assign different click-source" );
		
		return out;
	}
	public MatMeshBuilder getTexture (String name, String texture, HasApp onclick ) {
		
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
		new MatMeshBuilder( (String)a, (String)b ).ensureUVs() );
	
	final static float[] 
			glass = new float[] {0.0f, 0.0f, 0.0f, 1},
			wood = new float[] {0.8f, 0.8f, 0.8f, 1 },
			balcony = new float[] {0.2f, 0.2f, 0.2f, 1},
			moudling = new float[] {0.7f, 0.7f, 0.7f, 1},
			error = new float[] { 0.5f, 0.5f, 0.5f, 1.0f };

	public final MatMeshBuilder 
		WOOD  =  get( "wood", wood ),
		GLASS =  get( "glass", glass ),
		ERROR =  get( "error", error),
		MOULDING = get( "brick", moudling ),
		BALCONY =  get( "balcony", balcony);

	
	


}
