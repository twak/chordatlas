package org.twak.viewTrace.franken.style;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.JButton;
import javax.swing.JPanel;

import org.apache.commons.collections.map.HashedMap;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.franken.App;
import org.twak.viewTrace.franken.BlockApp;
import org.twak.viewTrace.franken.NetInfo;
import org.twak.viewTrace.franken.SelectedApps;
import org.twak.viewTrace.franken.App.AppMode;
import org.twak.viewTrace.franken.style.ui.JointUI;
import org.twak.viewTrace.franken.style.ui.JointUI.NetSelect;

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
				appInfo.put( k, new AppInfo (k) );
			}
		}
	}

	public static class AppInfo {
		
		public MultiModal dist;
		public Class bakeWith;
		
		public AppInfo (Class bakeWith) {
			this.bakeWith = bakeWith;
			this.dist = new MultiModal( NetInfo.get( bakeWith ) );
			this.dist.newMode();
		}
	}
	
	public JointDistribution( NetInfo ignore ) {
		rollJoint();
	}
	
//	private void install( BlockApp app ) {
//		this.root = app;
//		install (Collections.singletonList( app ));
//	}
	
	private void install (List<App> apps) {
		
		List<App> children = new ArrayList();

		for (App a : apps) {
			a.appMode = AppMode.Net;
			a.styleSource = this;
			children.addAll( a.getDown().valueList() );
		}
		
		if (!children.isEmpty())
			install (children);
	}

	public boolean install( SelectedApps root ) {
		this.root =  (BlockApp) root.findRoots().iterator().next();
		install (Collections.singletonList( this.root ));
		
		return true;
	}
	
	public void redraw() {

		MultiMap<App, App> bakeWith = new MultiMap<>();

		for ( Joint j : joints )
			findBake( j, Collections.singletonList( root ), bakeWith, new HashMap<>() );

		Random randy = new Random();

		for ( App building : root.getDown().valueList() )
			redraw( Collections.singletonList( building ), new HashSet<>(), drawJoint( randy ), randy, bakeWith );
	}

	public void updateJointProb() {
		totalJointProbability = joints.stream().mapToDouble( j -> j.probability ).sum();
	}
	
	public Joint rollJoint () {
		
		int ni = 1;
		
		String name = "joint";
		
		while (true) {
			
			boolean exists = false;
			for (Joint j : joints)
				exists |= j.name.equals( name );
			
			if (!exists)
				break;
			
			name= "joint " + ni++;
		};
		
		Joint j = new Joint (name,  JointUI.nets.stream().map( n -> n.klass ).collect(Collectors.toList()) );
		
		joints.add(j);
		updateJointProb();
		return j;
	}
	
	public void findBake( Joint j, List<App> current, MultiMap<App, App> bakeWith, Map<Class, App> parents ) {

		List<App> next = new ArrayList<>();

		for ( App a : current ) {
			
			parents.put( a.getClass(), a );
			Class bw = j.appInfo.get( a.getClass() ).bakeWith;
			if ( bw != null ) {

				if ( parents.get( bw ) == null )
					throw new Error();

				bakeWith.put( parents.get( bw ), a );
			}

			next.addAll( a.getDown().valueList() );
		}

		if (!next.isEmpty())
			findBake( j, next, bakeWith, parents );
	}

	private void redraw( List<App> as, Set<App> drawn, Joint j, Random random, MultiMap<App, App> bakeWith ) {

		List<App> next = new ArrayList<>();

		for ( App a : as ) {
			
			if (drawn.contains ( a ) )
				continue; // pre-baked
			
			drawn.add( a );
			
			a.styleZ = j.appInfo.get( a.getClass() ).dist.draw( random, a );

			MultiMap<Class, App> bakeTogether = new MultiMap<>();
			for (App b : bakeWith.get( a ))
				bakeTogether.put( b.getClass(), b );
			
			for (Class c : bakeTogether.keySet()) {
				double[] val = j.appInfo.get( c ).dist.draw( random, null );
				for (App b : bakeTogether.get( c ) ) {
					a.styleZ = val;
				}
			}
			
			next.addAll( a.getDown().valueList() );
		}

		if (!next.isEmpty() )
			redraw( next, drawn, j, random, bakeWith );
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
		JPanel out = new JPanel(new ListDownLayout() );
		
		JButton but = new JButton( "edit joint" );
		but.addActionListener( e -> new JointUI(this, update ).openFrame() );
		out.add( but );

		JButton redaw = new JButton( "redraw" );
		redaw.addActionListener( new ActionListener() {
			@Override
			public void actionPerformed( ActionEvent e ) {
				redraw();
				update.run();
			}
		} );
		out.add( redaw );

		return out;
	}

}
