package org.twak.viewTrace.franken;

import java.util.List;

import org.twak.tweed.gen.SuperEdge;
import org.twak.tweed.gen.SuperFace;
import org.twak.tweed.gen.skel.SkelGen;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.HalfMesh2.HalfEdge;

public class BuildingApp extends App {

	public SkelGen parent;
	
	public BuildingApp( SuperFace superFace ) {
		super( superFace );
	}

	public BuildingApp( BuildingApp buildingApp ) {
		super (buildingApp);
		this.parent = buildingApp.parent;
	}

	@Override
	public App copy() {
		return new BuildingApp( this );
	}

	@Override
	public App getUp() {
		return parent.app;
	}

	@Override
	public MultiMap<String, App> getDown() {
		
		MultiMap<String, App> down = new MultiMap<>();
		
		SuperFace sf = (SuperFace)hasA;
		
		down.put( "roof",  sf.mr.app.greebles ); 
		
		for (HalfEdge e : sf) 
			down.put ( "facade", ((SuperEdge)e).toEdit.appLabel );
		
		return down;
	}

	@Override
	public void computeBatch( Runnable whenDone, List<App> batch ) {
		whenDone.run();
	}
}
