package org.twak.viewTrace.facades;

import org.twak.siteplan.jme.MeshBuilder;

public class MatMeshBuilder extends MeshBuilder {

	float[] color;
	String name;
	
	public MatMeshBuilder (String name, float[] color) {
		this.name = name;
		this.color = color;
	}

	
//	public static Cache<float[], MatMeshBuilder> buildCache(Map <float[], MatMeshBuilder> store,  String mat ) {
//		return new Cach<float[], MatMeshBuilder> (store, c -> new MatMeshBuilder(mat, c) );
//	}
	
}
