package org.twak.viewTrace.franken;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.ProgressMonitor;

import org.twak.tweed.TweedFrame;
import org.twak.tweed.gen.skel.SkelGen;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.ui.AutoEnumCombo;
import org.twak.utils.ui.AutoEnumCombo.ValueSet;
import org.twak.utils.ui.Cancellable;
import org.twak.utils.ui.ColourPicker;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.facades.HasApp;
import org.twak.viewTrace.franken.App.AppMode;
import org.twak.viewTrace.franken.style.ConstantStyle;
import org.twak.viewTrace.franken.style.GaussStyle;
import org.twak.viewTrace.franken.style.JointStyle;
import org.twak.viewTrace.franken.style.MultiModal;
import org.twak.viewTrace.franken.style.StyleSource;


public class SelectedApps extends ArrayList<App>{
	
	public App exemplar;
	
	public SelectedApps(App app) {
		add(app);
		exemplar = app;
	}
	
	public SelectedApps() {}
	
	public SelectedApps( List<App> list ) {
		super (new ArrayList (new LinkedHashSet<>(list) ) );
		exemplar = list.get( 0 );
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
		SelectedApps sa = new SelectedApps();
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
			out.put( name, new SelectedApps(as.get( name ) ) );
			
		return out;
	}
	
	public void computeAll( Runnable globalUpdate, ProgressMonitor m ) {
		
		MultiMap<Integer, App> todo = new MultiMap<>();
		int i = NetInfo.evaluationOrder.indexOf( get(0).getClass() );
		todo.putAll( i , this );
		
		App.computeWithChildren( i, todo, globalUpdate );
	}
	
	public JPanel createUI( Runnable update_ ) {

		Runnable update = new Runnable() {
			@Override
			public void run() {
				
				System.out.println("selected apps createUI shim");
				
				if (exemplar instanceof BlockApp)
					for (App building : exemplar.getDown().valueList())
						building.isDirty = true;
				else
					for (App a : SelectedApps.this)
						a.markDirty();
				
				update_.run();
			}
		};
		
		JPanel top = new JPanel(new ListDownLayout() );
		JPanel main = new JPanel(new BorderLayout() );
		
		JPanel options = new JPanel();

		top.add( new JLabel( exemplar.name +" ("+size()+" selected)"), BorderLayout.NORTH );
		
		
		SelectedApps ups = findUp();
		Map<String, SelectedApps> downs = findDown();
		
		JPanel upDown = new JPanel(new GridLayout(1, 1 + downs.size() ) );
		
		if ( !ups.isEmpty() ) {
			JButton up   = new JButton("↑" + ups.exemplar.name);
			up.addActionListener( e -> TweedFrame.instance.tweed.frame.setGenUI( findUp().createUI ( update) ));
			upDown.add( up, BorderLayout.WEST);
		}
		
		
		if (downs != null)
		for (String wayDown : downs.keySet()) {
			JButton down = new JButton("↓ "+wayDown+"("+downs.get( wayDown ).size()+")");
			upDown.add( down );
			down.addActionListener( e -> TweedFrame.instance.tweed.frame.setGenUI( downs.get( wayDown ).createUI ( update) ) );
		}
		
		top.add(upDown);
		
		options.setLayout( new ListDownLayout() );
		
		AutoEnumCombo combo = new AutoEnumCombo( exemplar.appMode, new ValueSet() {
			public void valueSet( Enum num ) {
				
				for (App a : SelectedApps.this)
					a.appMode = (AppMode) num;
				
				options.removeAll();
				options.setLayout( new ListDownLayout() );

				buildLayout(exemplar.appMode, options, () -> refresh ( update ) );
				
				options.repaint();
				options.revalidate();
				
				new Thread("combo app select") {
					public void run() {
						refresh( update );
					};
				}.start();
			}
		}, "texture", exemplar.getValidAppModes() );
		
		buildLayout(exemplar.appMode, options, () -> refresh( update ) );
		
//		if ( NetInfo.get( exemplar ).resolution > 0)
		top.add(combo); // building doesn't have options yet
		
		main.add( top, BorderLayout.NORTH );
		main.add( options, BorderLayout.CENTER );

		return main;
	}

	protected void refresh( Runnable update ) {
		new Thread ( () ->  SelectedApps.this.computeAll(update, null) ).start();
	}

	private void buildLayout( AppMode appMode, JPanel out, Runnable update ) {
		
		for (App a : this)
			a.appMode = appMode;
		
		switch (appMode) {
		case Off:
			JButton col = new JButton("color");
			
			for (App a : SelectedApps.this) {
				a.texture = null;
			}
			
			col.addActionListener( e -> new ColourPicker(null, exemplar.color) {
				@Override
				public void picked( Color color ) {
					for (App a : SelectedApps.this) {
						a.color = color;
						a.texture = null;
					}
					update.run();
				}
			} );
			out.add( col );
			break;
		case Bitmap:
		default:
			out.add( new JLabel("no options") );
			break;
		case Net:
			out.add( createDistEditor(update) );
			
			break;
		}
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
	
	private Component createDistEditor( Runnable update ) {
		
		JPanel out = new JPanel( new BorderLayout() );
		JPanel options = new JPanel();
		
		JPanel north = new JPanel( new ListDownLayout() );
		
		AutoEnumCombo combo = new AutoEnumCombo( ss2Klass.get(exemplar.styleSource.getClass()), new ValueSet() {
			public void valueSet( Enum num ) {
				
				StyleSources sss = (StyleSources) num;
				StyleSource ss;
				
				if (exemplar.styleSource.getClass() == sss.klass)
					ss = exemplar.styleSource;
				else
					ss = sss.instance(exemplar);

				boolean changed = false;
				
				if ( !ss.install( SelectedApps.this ) ) {

					for ( App a : SelectedApps.this ) {
						changed |= a.styleSource != ss;
						a.styleSource = ss;
					}
				}

				options.removeAll();
				options.setLayout( new BorderLayout() );
				options.add( ss.getUI(update, SelectedApps.this), BorderLayout.CENTER );
				options.repaint();
				options.revalidate();
				
				if (changed)
					update.run();
			}

		}, "distribution:" );
		
		combo.fire();
		
		north.add( exemplar.createUI( update, SelectedApps.this ) );
		north.add( combo );
		
		out.add( north, BorderLayout.NORTH );
		out.add( options, BorderLayout.CENTER );
		
		return out;
	}
}
