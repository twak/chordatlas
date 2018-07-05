package org.twak.tweed.gen.skel;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

import org.twak.viewTrace.facades.FRect;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.franken.App;
import org.twak.viewTrace.franken.BlockApp;
import org.twak.viewTrace.franken.FacadeTexApp;
import org.twak.viewTrace.franken.PanesTexApp;
import org.twak.viewTrace.franken.RoofTexApp;

public class AppStore {

	Map<Class, Map<Object, App>> cache = new LinkedHashMap<>();
	
	public <E extends App> E get ( Class<? extends E> i1, Object i2 ) {
		Map<Object, App> cache2 = cache.get( i1 );
		if ( cache2 == null )
			cache.put( i1, cache2 = new WeakHashMap<Object, App>() );

		App o2 = cache2.get( i2 );
		if ( o2 == null )
			cache2.put( i2, o2 = create( i1, i2 ) );

		return (E) o2;
	}

	public void put( Class i1, Object i2, App start0 ) {
		
		Map<Object, App> cache2 = cache.get( i1 );
		if ( cache2 == null )
			cache.put( i1, cache2 = new WeakHashMap<Object, App>() );

		cache2.put( i2, start0 );
	}

	public App create( Class k, Object o ) {
		
		System.out.println("appstore creating " + k.getSimpleName() );
		
		try {
			return (App) k.getConstructor( o.getClass(), AppStore.class ).newInstance( o, this );
		} catch ( Throwable th ) {
			th.printStackTrace();
		}
		throw new Error();
	}

	public <E extends App> void clear( Class<? extends E> k, Object o ) {
		cache.get(k).put(o, null);
	}

	public <E extends App> void set( Class<? extends E> k, Object o, E e ) {
		put(k, o, e );
	}

	/**
	 * When someone clicks on geometry with userdata GreebleSkel.Appearance of a class, what do we show in the UI?
	 */
	private static final Map <Class, Class> uiLookup = new HashMap<>();
	static {
		uiLookup.put( MiniFacade.class, FacadeTexApp.class );
		uiLookup.put( MiniRoof.class  , RoofTexApp  .class );
		uiLookup.put( FRect.class     , PanesTexApp .class );
		uiLookup.put( SkelGen.class   , BlockApp    .class );
	}
	
	public App uiAppFor( Object o) {
		return get (uiLookup.get(o.getClass()), o);
	}
	

//	public static void main (String[] args) {
//		
//		FRect one = new FRect(1,2,3,4, null);
//		
//		Map<FRect, String> map = new WeakIdentityHashMap<>(1000);
//		
//		map.put( one, "foo" );
//		
//		System.out.println(map.get( one ));
//		one.x = 10000;
//		System.out.println(map.get( one ));
//		
//	}
}
