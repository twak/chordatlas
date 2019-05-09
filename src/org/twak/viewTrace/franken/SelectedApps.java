package org.twak.viewTrace.franken;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.ProgressMonitor;
import javax.swing.border.LineBorder;

import org.geotools.data.Join;
import org.twak.tweed.TweedFrame;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.ui.AutoEnumCombo;
import org.twak.utils.ui.AutoEnumCombo.ValueSet;
import org.twak.utils.ui.ListDownLayout;
import org.twak.utils.ui.Rainbow;
import org.twak.viewTrace.franken.App.TextureMode;
import org.twak.viewTrace.franken.style.ConstantStyle;
import org.twak.viewTrace.franken.style.GaussStyle;
import org.twak.viewTrace.franken.style.JointStyle;
import org.twak.viewTrace.franken.style.MultiModal;
import org.twak.viewTrace.franken.style.StyleSource;


public class SelectedApps extends ArrayList<App>{
	
	public App exemplar;
	
	public Runnable geometryUpdate;
	
	public SelectedApps(App app, Runnable globalUpdate) {
		add(app);
		exemplar = app;
		this.geometryUpdate = globalUpdate;
	}
	
	private SelectedApps(Runnable globalUpdate2) {
		this.geometryUpdate = globalUpdate2;
	}
	
	private SelectedApps (SelectedApps sa) {
		this.exemplar = sa.exemplar;
		this.geometryUpdate = sa.geometryUpdate;
		addAll(sa);
	}
	
	private SelectedApps( Collection<App> list, Runnable globalUpdate ) {
		super (new ArrayList (new LinkedHashSet<>(list) ) );
		this.geometryUpdate = globalUpdate;
		exemplar = list.iterator().next();
	}

	@Override
	public boolean add( App e ) {
		if (exemplar == null)
			exemplar= e;
		return super.add( e );
	}
	
	private SelectedApps findUp() {
		
		Set<App> ups = new LinkedHashSet<>();
		
		for (App a : this) {
			App up = a.getUp();
			if (up != null)
				ups.add(up);
		}
		SelectedApps sa = new SelectedApps( geometryUpdate);
		for (App a : ups)
			sa.add( a );
		
		return sa;
	}
	
	private Map<String, SelectedApps> findDown () {
		MultiMap<String, App> as = new MultiMap<>();
		
		for (App a : this) 
			as.putAll( a.getDown() );
		
		Map<String, SelectedApps> out = new LinkedHashMap<>();
		for (String name : as.keySet())
			out.put( name, new SelectedApps(as.get( name ), geometryUpdate ) );
			
		return out;
	}
	
	// made some changes so that multiple SelectedApps can run
	// computeTexturesNewThread() and not interfere with each other.

	static Boolean computing = false;

	public void computeTexturesNewThread() {
		new Thread () {
			@Override
			public void run() {
				computeTextures( null );
			}
			
			@Override
			public String toString() {
				return "SelectedApps.refresh";
			}
			
		}.start(); 
	}
	
	public void computeTextures( ProgressMonitor m ) {
		synchronized(computing) {
			computing = true;

			MultiMap<Integer, App> todo = new MultiMap<>();
			int i = NetInfo.evaluationOrder.indexOf( get(0).getClass() );
			todo.putAll( i , this );

			System.out.println("computing textures for " + get(0));

			App.computeWithChildren( i, todo, geometryUpdate );

			System.out.println("exit compute textures for " + get(0));

			computing = false;
		}
	}
	
	public void showUI() {
		TweedFrame.instance.tweed.frame.setGenUI( createUI() );
	}
	
	public JPanel createUI() {

		
		JPanel top = new JPanel(new ListDownLayout() );
		JPanel main = new JPanel(new BorderLayout() );
		
		JPanel options = new JPanel();

		JPanel countPanel = new JPanel(new BorderLayout() );
		
		JLabel jl = new JLabel( exemplar.name +" material ("+size()+")");
		jl.setFont(jl.getFont().deriveFont(jl.getFont().getStyle() | Font.BOLD));

		countPanel.add( jl, BorderLayout.SOUTH );
		
		JPanel countEast = new JPanel(new GridLayout( 1, 2 ));
		
		JToggleButton joint = new JToggleButton ("joint");
		countEast.add( joint );
		
		BlockApp b =  (BlockApp) findRoots().iterator().next();
		joint.setSelected( b.styleSource instanceof JointStyle );
		
		joint.addActionListener( new ActionListener() {
			@Override
			public void actionPerformed( ActionEvent e ) {
				
				if (b.styleSource instanceof JointStyle) 
					b.setIndp( SelectedApps.this );
				else
					b.setJoint( SelectedApps.this );
			}
		} );
		
		
		JButton all = new JButton ("all");
		all.addActionListener( e -> TweedFrame.instance.tweed.frame.setGenUI( findAll( exemplar.getClass() ).createUI() ) );
		countEast.add( all );
		countPanel.add(countEast, BorderLayout.EAST);
		
		top.add( countPanel, BorderLayout.NORTH );
		
		JPanel nets = new JPanel(new GridLayout(1, NetInfo.evaluationOrder.size()-1 ) );
		
		int currentIndex = NetInfo.evaluationOrder.indexOf( exemplar.getClass() );
		
		for (Class<? extends App> k : NetInfo.evaluationOrder) {
			
			NetInfo target = NetInfo.get( k );
			
			if (!target.visible)
				continue;
			
			int targetIndex = NetInfo.evaluationOrder.indexOf( k );
			
			JButton j = new JButton( new ImageIcon( target.icon ) );
			
			SelectedApps sa = new SelectedApps(this);
			
			if (targetIndex < currentIndex ) {
				while ( ! sa.isEmpty() && sa.exemplar.getClass() != k ) 
					sa = sa.findUp();
				
				if (sa.isEmpty())
					sa = findAll( k );
			}
			else if ( targetIndex > currentIndex) {
				
				sa  = findDown (k, sa);
				
				if (sa == null )
					sa = findAll(k);
			}
			
			
			if (target == exemplar.getNetInfo()) {
				j.setBorder( new LineBorder(  Rainbow.rainbow[4], 3 ) );
			}
			
			j.setToolTipText( target.name + (sa == null ? "" : ("(" +sa.size()+")") ) );
			j.setEnabled( sa != null);
			
			nets.add(j);
			
			SelectedApps sa_ = sa;
			
			j.addActionListener( new ActionListener() {
				@Override
				public void actionPerformed( ActionEvent e ) {
					TweedFrame.instance.tweed.frame.setGenUI( sa_.createUI() );
				}
			} );
		}
		
		top.add(nets);
		
		options.setLayout( new ListDownLayout() );
		
		AutoEnumCombo combo = new AutoEnumCombo( exemplar.appMode, new ValueSet() {
			public void valueSet( Enum num ) {
				
				for (App a : SelectedApps.this) {
					a.appMode = (TextureMode) num;
					if (a.appMode == TextureMode.Net && a.styleSource == null)
						a.styleSource = new GaussStyle( a.getClass() );
				}
				
				
				
				options.removeAll();
				options.setLayout( new ListDownLayout() );

				buildLayout(exemplar.appMode, options, new Runnable() {
					
					@Override
					public void run() {
						markDirty();
						computeTexturesNewThread ();
					}
					
					@Override
					public String toString() {
						return "combo in selected apps";
					}
				} );
				
				options.repaint();
				options.revalidate();
				
				new Thread("combo app select") {
					public void run() {
						markDirty();
						computeTexturesNewThread();
					};
				}.start();
			}
		}, "texture:", exemplar.getValidAppModes() );
		
		buildLayout(exemplar.appMode, options, new Runnable() {
			
			@Override
			public void run() {
				markDirty();
				computeTexturesNewThread();
			}
			@Override
			public String toString() {
				return "SelectedApps buildLayout";
			}
		});
		
		if (exemplar.showTextureOptions())
			top.add(combo);
		
		main.add( top, BorderLayout.NORTH );
		main.add( options, BorderLayout.CENTER );

		return main;
	}

	public void markDirty() {
		if (exemplar instanceof BlockApp)
			for (App building : exemplar.getDown().valueList())
				((BuildingApp)building).isGeometryDirty = true;
		else
			for (App a : SelectedApps.this)
				a.markGeometryDirty();
	}

	private void buildLayout( TextureMode appMode, JPanel out, Runnable update ) {

		for ( App a : this )
			a.appMode = appMode;

		out.add( createEditor( update ) );
	}

	private enum StyleSources {
		
		Gaussian (GaussStyle.class), 
		Constant (ConstantStyle.class), 
		MultiModal (MultiModal.class),
		Joint (JointStyle.class);
		
		Class<? extends StyleSource> klass;
		
		StyleSources (Class<?> klass) {
			this.klass = (Class<? extends StyleSource> ) klass;
		}
		
		public StyleSource instance(App app) {
			try {
				return klass.getConstructor( NetInfo.class ).newInstance( NetInfo.get( app ) );
			} catch ( Throwable e ) {
				e.printStackTrace();
				return null;
			}
		}
	}
	
	static Map<Class, StyleSources> ss2Klass = new HashMap<>();
	static {
		for (StyleSources ss : StyleSources.values()) 
			ss2Klass.put( ss.klass, ss );
	}
	
	public Set<App> findRoots() {
		
		Set<App> current = new HashSet<>(this);
		
		while (true) {
			Set<App> ups = new HashSet<>();
			
			for (App a : current)
				if (a.getUp() != null)
					ups.add(a.getUp());
			
			if (ups.isEmpty())
				return current;
			
			current = ups;
		}
	}

	private SelectedApps findAll( Class<? extends App> k ) {
		return findDown (k, new SelectedApps(findRoots(), geometryUpdate));
	}

	private SelectedApps findDown( Class k, SelectedApps sa ) {
		
		if (sa.exemplar.getClass() == k)
			return sa;
		
		for (SelectedApps sa2 : sa.findDown().values()) {
			SelectedApps out = findDown(k, sa2);
			if (out != null)
				return out;
		}
		
		return null;
	}
	
	private Component createEditor( Runnable update ) {
		
		JPanel out = new JPanel( new BorderLayout() );
		JPanel options = new JPanel();
		
		JPanel north = new JPanel( new ListDownLayout() );
		
		north.add( exemplar.createUI( update, SelectedApps.this ) );
		
		if ( exemplar.appMode == TextureMode.Net ) {
			
			exemplar.styleSource = exemplar.styleSource.copy(); // unlink from any any previous multiples
			
			options.removeAll();
			options.setLayout( new BorderLayout() );
			options.add( exemplar.styleSource.getUI( update, SelectedApps.this ), BorderLayout.CENTER );
			options.repaint();
			options.revalidate();
			
			
			boolean changed = false;
			
			for ( App a : SelectedApps.this ) {
				changed |= a.styleSource != exemplar.styleSource;
				a.styleSource = exemplar.styleSource;
			}
			
			if ( changed )
				update.run();
			
//			AutoEnumCombo combo = new AutoEnumCombo( ss2Klass.get( exemplar.styleSource.getClass() ), new ValueSet() {
//				public void valueSet( Enum num ) {
//
//					StyleSources sss = (StyleSources) num;
//					StyleSource ss;
//
//					if ( exemplar.styleSource.getClass() == sss.klass )
//						ss = exemplar.styleSource;
//					else
//						ss = sss.instance( exemplar );
//
//					boolean changed = false;
//
//					if ( !ss.install( SelectedApps.this ) ) {
//
//						for ( App a : SelectedApps.this ) {
//							changed |= a.styleSource != ss;
//							a.styleSource = ss;
//						}
//					}
//
//					options.removeAll();
//					options.setLayout( new BorderLayout() );
//					options.add( ss.getUI( update, SelectedApps.this ), BorderLayout.CENTER );
//					options.repaint();
//					options.revalidate();
//
//					if ( changed )
//						update.run();
//				}
//
//			}, "distribution:" );

//			combo.fire();
//			north.add( combo );
		}		
		out.add( north, BorderLayout.NORTH );
		out.add( options, BorderLayout.CENTER );
		
		return out;
	}
}
