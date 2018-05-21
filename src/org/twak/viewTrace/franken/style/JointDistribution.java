package org.twak.viewTrace.franken.style;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.swing.JPanel;

import org.apache.commons.collections.map.HashedMap;
import org.twak.utils.collections.MultiMap;
import org.twak.viewTrace.franken.App;
import org.twak.viewTrace.franken.BlockApp;

public class JointDistribution implements StyleSource {

	public transient BlockApp root;

	double totalJointProbability = 0;

	public String name;
	
	public List<Joint> joints = new ArrayList<>();

	public static class Joint {
		Map<Class, AppInfo> appInfo = new HashedMap();
		double probability;
	}

	static class AppInfo {
		MultiModal dist;
		Class bakeWith;
	}
	

	public JointDistribution( BlockApp app, Runnable globalUpdate ) {
		init( app );
	}

	private void init( BlockApp app ) {
		this.root = app;
	}

	public void install() {

		MultiMap<App, App> bakeWith = new MultiMap<>();

		for ( Joint j : joints )
			findBake( j, Collections.singletonList( root ), bakeWith, new HashMap<>() );

		Random randy = new Random();

		for ( App building : root.getDown().valueList() )
			install( Collections.singletonList( building ), drawJoint( randy ), randy, bakeWith );
	}

	public void updateJointProb() {
		totalJointProbability = joints.stream().mapToDouble( j -> j.probability ).sum();
	}
	
	public void findBake( Joint j, List<App> current, MultiMap<App, App> bakeWith, Map<Class, App> parents ) {

		List<App> next = new ArrayList<>();

		for ( App a : current ) {
			
			a.styleSource = null;
			
			parents.put( a.getClass(), a );
			Class bw = j.appInfo.get( a.getClass() ).bakeWith;
			if ( bw != null ) {

				if ( parents.get( bw ) == null )
					throw new Error();

				bakeWith.put( parents.get( bw ), a );
			}

			next.addAll( a.getDown().valueList() );
		}

		findBake( j, next, bakeWith, parents );
	}

	private void install( List<App> as, Joint j, Random random, MultiMap<App, App> bakeWith ) {

		List<App> next = new ArrayList<>();

		for ( App a : as ) {
			
			if (a.styleSource == this)
				continue; // pre-baked
			
			a.styleSource = this;
			a.styleZ = j.appInfo.get( a.getClass() ).dist.draw( random, a );

			MultiMap<Class, App> bakeTogether = new MultiMap<>();
			for (App b : bakeWith.get( a ))
				bakeTogether.put( b.getClass(), b );
			
			for (Class c : bakeTogether.keySet()) {
				double[] val = j.appInfo.get( c ).dist.draw( random, null );
				for (App b : bakeTogether.get( c ) ) {
					b.styleSource = this;
					a.styleZ = val;
				}
			}
			
			next.addAll( a.getDown().valueList() );
		}

		install( next, j, random, bakeWith );
	}

	private Joint drawJoint( Random random ) {

		double d = random.nextDouble() * totalJointProbability;

		double p = 0;
		for ( Joint j : joints ) {
			p += j.probability;
			if ( p > d )
				return j;
		}

		throw new Error();
	}

	@Override
	public double[] draw( Random random, App app ) {
		return app.styleZ; // do nothing
	}

	@Override
	public JPanel getUI( Runnable update ) {
		return new JPanel();
	}

}
