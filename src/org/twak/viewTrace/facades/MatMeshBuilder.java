package org.twak.viewTrace.facades;

import org.twak.siteplan.jme.MeshBuilder;

public class MatMeshBuilder extends MeshBuilder {

	float[] color;
	String name, texture;
	public HasApp app;
	
	/**
	 * TextureAtlas
	 * 
	 */
	
	public MatMeshBuilder (String name, float[] color) {
		this.name = name;
		this.color = color;
		
		if (color == null)
			throw new Error("bad color");
	}
	
	public MatMeshBuilder (String name, String texture) {
		this.name = name;
		this.color = null;
		this.texture = texture;
		
		if (texture == null)
			throw new Error("bad texture");
	}

//	public static Cache<float[], MatMeshBuilder> buildCache(Map <float[], MatMeshBuilder> store,  String mat ) {
//		return new Cach<float[], MatMeshBuilder> (store, c -> new MatMeshBuilder(mat, c) );
//	}
	
}
