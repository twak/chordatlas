package org.twak.viewTrace.franken;

import java.util.List;

import org.twak.tweed.gen.SuperEdge;
import org.twak.tweed.gen.SuperFace;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.HalfMesh2.HalfEdge;

public class BuildingApp extends App {

	public BuildingApp( SuperFace superFace ) {
		super( superFace, "building", null, 0, 0 );
	}

	public BuildingApp( BuildingApp buildingApp ) {
		super (buildingApp);
	}

	@Override
	public App copy() {
		return new BuildingApp( this );
	}

	@Override
	public App getUp() {
		return null;
	}

	@Override
	public MultiMap<String, App> getDown() {
		
		MultiMap<String, App> down = new MultiMap<>();
		
		SuperFace sf = (SuperFace)hasA;
		
		down.put( "roof", sf.mr.app ); 
		
		for (HalfEdge e : sf) 
			down.put ( "facade", ((SuperEdge)e).toEdit.app );
		
		return down;
	}

	@Override
	public void computeBatch( Runnable whenDone, List<App> batch ) {
		whenDone.run();
	}
}
