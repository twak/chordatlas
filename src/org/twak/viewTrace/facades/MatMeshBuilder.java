package org.twak.viewTrace.facades;

import org.twak.siteplan.jme.MeshBuilder;

public class MatMeshBuilder extends MeshBuilder {

	float[] color;
	String name, texture;
	
	public MatMeshBuilder (String name, float[] color) {
		this.name = name;
		this.color = color;
	}
	
	public MatMeshBuilder (String name, String texture) {
		this.name = name;
		this.color = null;
		this.texture = texture;
	}

//	public static Cache<float[], MatMeshBuilder> buildCache(Map <float[], MatMeshBuilder> store,  String mat ) {
//		return new Cach<float[], MatMeshBuilder> (store, c -> new MatMeshBuilder(mat, c) );
//	}
	
}
