package org.twak.viewTrace.facades;

public interface HasApp {

	static Appearance get( HasApp roofApp ) {
		try {
			return (Appearance) roofApp.getClass().getField( "app" ).get( roofApp );
		} catch ( Throwable e ) {
			e.printStackTrace();
		}
		return null;
	}

}
