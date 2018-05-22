package org.twak.viewTrace.franken;

import java.util.List;

import org.twak.tweed.gen.SuperFace;
import org.twak.tweed.gen.skel.SkelGen;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.HalfMesh2.HalfFace;

public class BlockApp extends App {

	public BlockApp( BlockApp buildingApp ) {
		super (buildingApp);
	}

	public BlockApp( SkelGen skelGen ) {
		super (skelGen );
	}

	@Override
	public App copy() {
		return new BlockApp( this );
	}

	@Override
	public App getUp() {
		return null;
	}

	@Override
	public MultiMap<String, App> getDown() {
		
		MultiMap<String, App> down = new MultiMap<>();
		SkelGen sg = (SkelGen) hasA;

		for (HalfFace sf : sg.block) {
			down.put( "building", ((SuperFace) sf).app );
		}
		
		return down;
	}

	@Override
	public void computeBatch( Runnable whenDone, List<App> batch ) {
		whenDone.run();
	}
}
