package org.twak.viewTrace.facades;

import java.util.List;

public interface HasApp {

	static Appearance get( HasApp roofApp ) {
		try {
			return (Appearance) roofApp.getClass().getField( "app" ).get( roofApp );
		} catch ( Throwable e ) {
			e.printStackTrace();
		}
		return null;
	}

	public List<HasApp> getAppChildren();
}
