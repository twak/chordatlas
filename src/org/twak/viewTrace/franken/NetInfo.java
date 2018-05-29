package org.twak.viewTrace.franken;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NetInfo {

	public String netName;
	public int sizeZ;
	public int resolution;
	public String name;
//	public Class<? extends App> appClass;
	
	public NetInfo( String name, int sizeZ, int resolution ) {
		this.name = name;
		this.sizeZ = sizeZ;
		this.resolution = resolution;
	}
	
	public final static Map  <Class<? extends App> , NetInfo> index = new HashMap<>();
	public final static List <Class<? extends App>> evaluationOrder = new ArrayList<>();
	
	static {
		index.put ( d( BlockApp.class        ), new NetInfo ( "block"          , 0, 0   ) );
		index.put ( d( BuildingApp.class     ), new NetInfo ( "building"       , 0, 0   ) );
		
		index.put ( d( FacadeLabelApp.class  ), new NetInfo ( "facade labels"  , 8, 256 ) );
		index.put ( d( FacadeTexApp.class    ), new NetInfo ( "facade textures", 8, 256 ) );
		index.put ( d( FacadeGreebleApp.class), new NetInfo ( "facade greebles", 0, 256 ) );
		index.put ( d( PanesLabelApp.class   ), new NetInfo ( "pane labels"    , 8, 256 ) );
		index.put ( d( PanesTexApp.class     ), new NetInfo ( "pane textures"  , 8, 256 ) );
		index.put ( d( RoofGreebleApp.class  ), new NetInfo ( "roof greebles"  , 8, 512 ) );
		index.put ( d( RoofTexApp.class      ), new NetInfo ( "roof"           , 8, 512 ) );
		
		index.put ( d( FacadeSuperApp.class  ), new NetInfo ( "facade super"   , 8, 256 ) );
		index.put ( d( RoofSuperApp.class    ), new NetInfo ( "roof super"     , 8, 256 ) );
	}
	
	private static Class<? extends App> d (Class<? extends App> k) {
		evaluationOrder.add( k );
		return k;
	}
	
	public static NetInfo get( App exemplar ) {
		return index.get(exemplar.getClass());
	}
	
	public static NetInfo get( Class exemplar ) {
		return index.get(exemplar);
	}

}
