package org.twak.viewTrace.franken.style;

import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.twak.utils.collections.MultiMap;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.franken.App;
import org.twak.viewTrace.franken.App.TextureMode;
import org.twak.viewTrace.franken.BlockApp;
import org.twak.viewTrace.franken.BuildingApp;
import org.twak.viewTrace.franken.DoorTexApp;
import org.twak.viewTrace.franken.FacadeGreebleApp;
import org.twak.viewTrace.franken.FacadeLabelApp;
import org.twak.viewTrace.franken.FacadeSuperApp;
import org.twak.viewTrace.franken.FacadeTexApp;
import org.twak.viewTrace.franken.NetInfo;
import org.twak.viewTrace.franken.PanesLabelApp;
import org.twak.viewTrace.franken.PanesTexApp;
import org.twak.viewTrace.franken.RoofGreebleApp;
import org.twak.viewTrace.franken.RoofSuperApp;
import org.twak.viewTrace.franken.RoofTexApp;
import org.twak.viewTrace.franken.SelectedApps;
import org.twak.viewTrace.franken.VeluxTexApp;
import org.twak.viewTrace.franken.style.ui.JointUI;

public class JointStyle implements StyleSource {

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
		public transient Set<Class> bake = new HashSet<>();
		
		public PerJoint() {
			System.out.println(">>>");
		}
		
		public PerJoint (NetProperties ns) {
			this.ns = ns;
			this.bakeWith = ns.klass == BlockApp.class ? ns.klass : BuildingApp.class; // shouldn't bake block anyway
			this.dist = new MultiModal( ns.klass );
			this.dist.newMode();
		}
		
		  private Object readResolve() {
			    bake = new HashSet<>();
			    return this;
		  }
	}

	public List<NetProperties> nets = new ArrayList<>();
	Map<Class, NetProperties> klass2Net = new HashMap<>();
	public NetProperties defaultNet;
	{
		nets.add (new NetProperties(BlockApp                 .class, false, false, true  ) );
		nets.add (new NetProperties(BuildingApp              .class, false, false, true  ) );
		nets.add (new NetProperties(FacadeLabelApp           .class, true , false, true  ) );
		nets.add (new NetProperties(FacadeGreebleApp         .class, false , false, true  ) );
		nets.add (defaultNet = new NetProperties(FacadeTexApp.class, true , true , true  ) );
		nets.add (new NetProperties(RoofTexApp               .class, true , true , true  ) );
		nets.add (new NetProperties(RoofGreebleApp           .class, false, false, true  ) );
		nets.add (new NetProperties(VeluxTexApp              .class, false , false, true  ) );
		nets.add (new NetProperties(PanesLabelApp            .class, true , false, true  ) );
		nets.add (new NetProperties(PanesTexApp              .class, true , false, true  ) );
		nets.add (new NetProperties(DoorTexApp               .class, true , false, true  ) );
		nets.add (new NetProperties(FacadeSuperApp           .class, true , false, false ) );
		nets.add (new NetProperties(RoofSuperApp             .class, true , false, false ) );
	}
	
	@Override
	public StyleSource copy() {
		return this; // we only have one joint for the entire block
	}
	
	public class NetProperties {
		
		public Class<? extends App> klass;
		public boolean show; // in the ui
		public boolean on; // do we run the GAN?
		public boolean medium; // are we on if set to medium
		
		
		public NetProperties( Class<? extends App> k, boolean s, boolean onByDefault, boolean medium ) {
			this.klass = k;
			this.show = s;
			this.on = onByDefault;
			this.medium = medium;
			
			klass2Net.put (k, this);
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

		public void setHigh() {
			this.on = true;
		}
		
		public void setMedium() {
			this.on = medium;
		}
		
		public void setLow() {
			Class c= (Class)klass;
			this.on = c == FacadeLabelApp.class
					|| c == FacadeTexApp.class || c == RoofTexApp.class;
		}
	}
	
	public JointStyle( NetInfo ignore ) {
		rollJoint();
	}

	public boolean install( SelectedApps root ) {
		
		nets.stream().forEach( n -> n.setMedium() );
		
		BlockApp ba = (BlockApp) root.findRoots().iterator().next();

		if ( this.root != ba ) {
			this.root = ba;
			redraw( );
		}
		
		return true;
	}
	
	Random randy = new Random();
	public void redraw() {
		
		for (Joint j : joints) {
			
			for (PerJoint pj : j.appInfo.values()) 
				pj.bake.clear();
			
			for (PerJoint pj : j.appInfo.values())
				if (pj.bakeWith != null) 
					j.appInfo.get( pj.bakeWith ).bake.add( pj.ns.klass );
		}
		
		root.markGeometryDirty();
		
		Random randy = new Random(System.nanoTime());

		root.styleSource = this; 
		
		for ( App building_ : root.getDown().valueList() )  {
			
			BuildingApp building = ((BuildingApp)building_);
			
			building.lastJoint = drawJoint( randy );
			
			building.updateDormers( randy );
			
			redraw( 1, new MultiMap<>( 1, building), building.lastJoint, randy );
		}
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
	
	public void process (App app, Random random) {
		
		app.bakeWith = new HashMap<>();
		
		setMode( app );
		app.styleSource = this;
		
		if (app instanceof BlockApp)
			return;
			
		Joint j = findParentOfClass (app, BuildingApp.class).lastJoint;
		
		PerJoint ai = j.appInfo.get( app.getClass() );
		Class bw = ai.bakeWith;
		
		if (bw != null && bw != app.getClass() ) {
			App a = findParentOfClass (app, bw);
			app.styleZ = a.bakeWith.get(app.getClass());
			if (app.styleZ == null)
				return; // initalisation: no parent yet
		}
		else
			app.styleZ = ai.dist.draw( random, app );
		
		for (Class c : ai.bake) {
			app.bakeWith.put( c, j.appInfo.get( c ).dist.draw( random, null ) );
		}
		
	}
	
	public <E extends App> E findParentOfClass (App app, Class<? extends E> klass) {
		if (app.getClass() == klass)
			return(E) app;
		
		return findParentOfClass( app.getUp( ), klass);
	}

	private void redraw( int stage,  MultiMap<Integer, App> todo, Joint j, Random random ) {

		
		if (stage >= NetInfo.evaluationOrder.size())
			return;
		
		for ( App a : todo.get( stage ) ) {
			
			process( a, random );
			
			for (App next : a.getDown().valueList())
				todo.put( NetInfo.evaluationOrder.indexOf( next.getClass() ), next );
		}

		redraw( stage + 1, todo, j, random );
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
		
		if (app.styleZ == null) { // app wasn't redraw'd earlier
			process( app, random );
		}
			
		return app.styleZ; // do nothing
	}

	@Override
	public JPanel getUI( Runnable update, SelectedApps sa ) {
		JPanel out = new JPanel(new ListDownLayout() );
		

		JPanel detail = new JPanel (new GridLayout( 1,  3 ));
		
		JButton low = new JButton( "low" );
		low.addActionListener( e -> nets.stream().forEach( n -> n.setLow() ) );
		detail.add(low);
		
		JButton medium = new JButton( "medium" );
		medium.addActionListener( e -> nets.stream().forEach( n -> n.setMedium() ) );
		detail.add(medium);
		
		JButton high = new JButton( "super" );
		high.addActionListener( e -> nets.stream().forEach( n -> n.setHigh() ) );
		detail.add(high);
		
		out.add(new JLabel("joint distribtion LOD:"));
		out.add(detail);
		
		JButton but = new JButton( "edit joint" );
		but.addActionListener( e -> new JointUI( sa, update ).openFrame() );
		out.add( but );

		JButton redaw = new JButton( "redraw distribution" );
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

	public void setMode( App appMode ) {
		NetProperties ns = klass2Net.get(appMode.getClass()); 
		appMode.appMode = (ns.on || !ns.show) ? TextureMode.Net : TextureMode.Off; // ui for block, building, not others
		appMode.styleSource = this;
	}

	@Override
	public void install( App app ) {
		process( app, randy );
	}

}
