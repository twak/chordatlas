package org.twak.viewTrace.franken;

import java.util.HashMap;
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
	
	public final static Map <Class<? extends App> , NetInfo> index = new HashMap<>();
	
	static {
		index.put ( FacadeLabelApp.class, new NetInfo ( "facade labels", 8, 256 ) );
		index.put ( BlockApp.class      , new NetInfo ( "block", 0, 0 ) );
		index.put ( BuildingApp.class   , new NetInfo ( "building", 0, 0  ) );
		index.put ( FacadeSuperApp.class   , new NetInfo ( "facade super", 8, 256 ) );
		index.put ( FacadeTexApp.class  , new NetInfo ( "facade textures", 8, 256 ) );
		index.put ( PanesLabelApp.class , new NetInfo ( "pane labels", 8, 256 ) );
		index.put ( PanesTexApp.class   , new NetInfo ( "pane textures", 8, 256 ) );
		index.put ( RoofApp.class       , new NetInfo ( "roof", 8, 512 ) );
	}
	
	public static NetInfo get( App exemplar ) {
		return index.get(exemplar.getClass());
	}
	
	public static NetInfo get( Class exemplar ) {
		return index.get(exemplar);
	}

}
