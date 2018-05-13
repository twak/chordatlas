package org.twak.viewTrace.facades;

import java.util.List;

import org.twak.viewTrace.franken.App;

public interface HasApp {

	static App get( HasApp roofApp ) {
		try {
			return (App) roofApp.getClass().getField( "app" ).get( roofApp );
		} catch ( Throwable e ) {
			e.printStackTrace();
		}
		return null;
	}

	public List<HasApp> getAppChildren();
}
