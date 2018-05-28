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

import javax.swing.JButton;
import javax.swing.JPanel;

import org.twak.utils.collections.MultiMap;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.franken.App;
import org.twak.viewTrace.franken.App.AppMode;
import org.twak.viewTrace.franken.BlockApp;
import org.twak.viewTrace.franken.BuildingApp;
import org.twak.viewTrace.franken.FacadeLabelApp;
import org.twak.viewTrace.franken.FacadeSuperApp;
import org.twak.viewTrace.franken.FacadeTexApp;
import org.twak.viewTrace.franken.NetInfo;
import org.twak.viewTrace.franken.PanesLabelApp;
import org.twak.viewTrace.franken.PanesTexApp;
import org.twak.viewTrace.franken.RoofTexApp;
import org.twak.viewTrace.franken.SelectedApps;
import org.twak.viewTrace.franken.style.ui.JointUI;

public class JointDistribution implements StyleSource {

	public transient BlockApp root;

	double totalJointProbability = 0;

	public List<Joint> joints = new ArrayList<>();

	public class Joint {
		
		public String name;
		
		public Map<Class, PerJoint> appInfo = new HashMap();
		public double probability = 0.5;
		
		public Joint( String name ) {
			this.name = name;
			for ( NetProperties ns : nets ) 
				appInfo.put( ns.klass, new PerJoint (ns ) );
		}
	}

	public static class PerJoint {
		
		NetProperties ns;
		public MultiModal dist;
		public Class bakeWith;
		
		public PerJoint (NetProperties ns) {
			this.ns = ns;
			this.bakeWith = ns.klass == BlockApp.class ? ns.klass : BuildingApp.class; // shouldn't bake block anyway
			this.dist = new MultiModal( NetInfo.get( ns.klass ) );
			this.dist.newMode();
		}
	}

	public List<NetProperties> nets = new ArrayList<>();
	public NetProperties defaultNet;
	{
		nets.add (new NetProperties(BlockApp      .class, false, false ) );
		nets.add (new NetProperties(BuildingApp   .class, false, false ) );
		nets.add (new NetProperties(FacadeLabelApp.class, true , false ) );
		nets.add (new NetProperties(FacadeSuperApp.class, true , false ) );
		nets.add (defaultNet = new NetProperties(FacadeTexApp  .class, true , true ) );
		nets.add (new NetProperties(PanesLabelApp .class, true , false ) );
		nets.add (new NetProperties(PanesTexApp   .class, true , false ) );
		nets.add (new NetProperties(RoofTexApp       .class, true , true  ) );
	}
	
	public static class NetProperties {
		
		public Class<? extends App> klass;
		public boolean show; // in the ui
		public boolean on; // do we run the GAN?
		
		public NetProperties( Class<? extends App> k, boolean s, boolean onByDefault ) {
			this.klass = k;
			this.show = s;
			this.on = onByDefault;
		}

		public App findExemplar (App root) {
			
			if (root.getClass() == klass)
				return root;
			
			return findExemplar( Collections.singletonList( root ) );
		}
		
		public App findExemplar (List<App> root) {
			
			List<App> next = new ArrayList<>();
			
			for (App a : root)
				for (App b : a.getDown().valueList())
					if (b.getClass() == klass)
						return b;
					else
						next.add(b);
			
			if (next.isEmpty())
				return null;
			
			return findExemplar( next );
		}
	}
	
	public JointDistribution( NetInfo ignore ) {
		rollJoint();
	}

	public boolean install( SelectedApps root ) {
		
		BlockApp ba = (BlockApp) root.findRoots().iterator().next();

		if ( this.root != ba ) {
			this.root = ba;
			redraw();
		}
		
		return true;
	}
	
	public void redraw() {
		
//		install(Collections.singletonList( this.root ));

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
		
		String name = "my dist";
		
		while (true) {
			
			boolean exists = false;
			for (Joint j : joints)
				exists |= j.name.equals( name );
			
			if (!exists)
				break;
			
			name= "dist " + ni++;
		};
		
		Joint j = new Joint (name);
		
		joints.add(j);
		updateJointProb();
		return j;
	}
	
	public void findBake( Joint j, List<App> current, MultiMap<App, App> bakeWith, Map<Class, App> parents ) {

		List<App> next = new ArrayList<>();

		for ( App a : current ) {
			
			parents.put( a.getClass(), a );
			
			PerJoint ai = j.appInfo.get( a.getClass() );
			Class bw = ai.bakeWith;
			if ( bw != null ) {

				if ( parents.get( bw ) == null )
					throw new Error();

				bakeWith.put( parents.get( bw ), a );
			}
			
			a.styleSource = this;
			a.appMode = ai.ns.on || !ai.ns.show ? AppMode.Net : AppMode.Off; // ui for block, building, not others

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
