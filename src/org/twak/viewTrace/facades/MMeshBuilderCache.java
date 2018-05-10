package org.twak.viewTrace.facades;

import org.twak.utils.Cach2;

public class MMeshBuilderCache extends Cach2<String, float[], MatMeshBuilder> {
	
	public MMeshBuilderCache() {
		super ( (a,b) -> new MatMeshBuilder( (String) a, (float[]) b ) );
	}
	
	public MatMeshBuilder get (String name, float[] col, HasApp onclick ) {
		
		MatMeshBuilder out = get( name, col );
		
		if (out.onclick == null)
			out.onclick = onclick;
		else if (out.onclick != onclick)
			System.err.println( "warning: attempt to assign different click-source" );
		
		return out;
	}
	public MatMeshBuilder get (String name, String texture, HasApp onclick ) {
		
		MatMeshBuilder out = textures.get( name, texture );
		
		if (out.onclick == null)
			out.onclick = onclick;
		else if (out.onclick != onclick)
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
