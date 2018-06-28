package org.twak.tweed.gen.skel;

import java.util.HashMap;
import java.util.Map;

import org.twak.utils.Cache2;
import org.twak.viewTrace.facades.FRect;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.franken.App;
import org.twak.viewTrace.franken.BlockApp;
import org.twak.viewTrace.franken.FacadeTexApp;
import org.twak.viewTrace.franken.PanesTexApp;
import org.twak.viewTrace.franken.RoofTexApp;

public class AppStore {

	
	private Cache2<Class, Object, App> appCache = new Cache2<Class, Object, App>() {

		@Override
		public App create( Class k, Object o ) {

			try {
				return (App) k.getConstructor( o.getClass() ).newInstance( o );
			} catch ( Throwable th ) {
				th.printStackTrace();
			}
			throw new Error();
			
//			Class oc = o.getClass();
//			if ( oc == MiniFacade.class )
//				if (k == FacadeTexApp.class)
//					return new FacadeTexApp( (MiniFacade) o );
//				else if ( k == Facade)
//			
		}
		
	};
	
	public <E extends App>  E get ( Class<? extends E> klass, Object o ) {
		return (E) appCache.get(klass, o);
	}

	public <E extends App> void clear( Class<? extends E> k, Object o ) {
		appCache.cache.get(k).put(o, null);
	}

	public <E extends App> void set( Class<? extends E> k, Object o, E e ) {
		appCache.put(k, o, e );
	}

	/**
	 * When someone clicks on this object, what do we show in the UI?
	 */
	private static final Map <Class, Class> uiLookup = new HashMap<>();
	static {
		uiLookup.put( MiniFacade.class, FacadeTexApp.class );
		uiLookup.put( MiniRoof.class, RoofTexApp.class );
		uiLookup.put( FRect.class, PanesTexApp.class );
		uiLookup.put( SkelGen.class, BlockApp.class );
	}
	
	public App uiAppFor( Object o) {
		return get (uiLookup.get(o.getClass()), o);
	}
}
