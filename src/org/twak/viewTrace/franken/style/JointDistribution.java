package org.twak.viewTrace.franken.style;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.swing.JButton;
import javax.swing.JPanel;

import org.apache.commons.collections.map.HashedMap;
import org.twak.utils.collections.MultiMap;
import org.twak.viewTrace.franken.App;
import org.twak.viewTrace.franken.BlockApp;
import org.twak.viewTrace.franken.SelectedApps;
import org.twak.viewTrace.franken.style.ui.JointUI;
import org.twak.viewTrace.franken.style.ui.MultiModalEditor;

public class JointDistribution implements StyleSource {

	public transient BlockApp root;

	double totalJointProbability = 0;

	
	public List<Joint> joints = new ArrayList<>();

	public static class Joint {
		public String name;
		public Map<Class, AppInfo> appInfo = new HashedMap();
		public double probability = 0.5;
		public Joint( String name2, List<Class<?>> klasses ) {
			this.name = name2;
			for (Class k : klasses) {
				appInfo.put( k, new AppInfo (null) );
			}
		}
	}

	public static class AppInfo {
		
		public MultiModal dist;
		public Class bakeWith;
		
		public AppInfo (Class bakeWith) {
			this.bakeWith = bakeWith;
		}
	}
	
	public JointDistribution( App ignore ) {
		init(null);
	}
	
	private void init( BlockApp app ) {
		this.root = app;
	}

	public boolean install( SelectedApps root ) {
		init( (BlockApp) root.findRoots().iterator().next() );
		return true;
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
	
	public Joint rollJoint ( String name, List<Class<?>> klasses) {
		Joint j = new Joint (name, klasses);
		joints.add(j);
		updateJointProb();
		return j;
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
		JPanel out = new JPanel();
		
		JButton but = new JButton( "edit joint" );
		but.addActionListener( e -> new JointUI(this, update ).openFrame() );
		out.add( but );
		
		return out;
	}

}
