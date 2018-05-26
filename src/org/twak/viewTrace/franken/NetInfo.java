package org.twak.viewTrace.franken;

import java.util.HashMap;
import java.util.Map;

public class NetInfo {

	public String netName;
	public int sizeZ;
	public int resolution;
	public String name;
//	public Class<? extends App> appClass;
	
	public NetInfo( String name, String netname, int sizeZ, int resolution ) {
		this.name = name;
		this.netName =netname;
		this.sizeZ = sizeZ;
		this.resolution = resolution;
	}
	
	public final static Map <Class<? extends App> , NetInfo> index = new HashMap<>();
	
	static {
		index.put ( FacadeLabelApp.class, new NetInfo ( "facade labels", "empty2windows_f005", 8, 256 ) );
		index.put ( BlockApp.class      , new NetInfo ( "block", null, 0, 0 ) );
		index.put ( BuildingApp.class   , new NetInfo ( "building", null, 0, 0  ) );
		index.put ( FacadeSuper.class   , new NetInfo ( "facade super", "super6", 8, 256 ) );
		index.put ( FacadeTexApp.class  , new NetInfo ( "facade textures", "facade_windows_f000", 8, 256 ) );
		index.put ( PanesLabelApp.class , new NetInfo ( "pane labels", "dows2", 8, 256 ) );
		index.put ( PanesTexApp.class   , new NetInfo ( "pane textures", "dows1", 8, 256 ) );
		index.put ( RoofApp.class       , new NetInfo ( "roof", "roofs6", 8, 512 ) );
	}
	
	public static NetInfo get( App exemplar ) {
		return index.get(exemplar.getClass());
	}
	
	public static NetInfo get( Class exemplar ) {
		return index.get(exemplar);
	}

}
