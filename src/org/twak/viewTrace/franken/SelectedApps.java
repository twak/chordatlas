package org.twak.viewTrace.franken;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.ProgressMonitor;
import javax.swing.border.LineBorder;

import org.twak.tweed.TweedFrame;
import org.twak.tweed.gen.skel.AppStore;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.ui.AutoEnumCombo;
import org.twak.utils.ui.AutoEnumCombo.ValueSet;
import org.twak.utils.ui.ColourPicker;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.franken.App.AppMode;
import org.twak.viewTrace.franken.style.ConstantStyle;
import org.twak.viewTrace.franken.style.GaussStyle;
import org.twak.viewTrace.franken.style.JointStyle;
import org.twak.viewTrace.franken.style.MultiModal;
import org.twak.viewTrace.franken.style.StyleSource;

import com.jme3.texture.Image;


public class SelectedApps extends ArrayList<App>{
	
	public App exemplar;
	
	public AppStore ass;
	
	
	public SelectedApps(App app, AppStore ac) {
		add(app);
		exemplar = app;
		this.ass = ac;
	}
	
	public SelectedApps(AppStore ac) {
		this.ass = ac;
	}
	
	public SelectedApps (SelectedApps sa) {
		this.exemplar = sa.exemplar;
		this.ass = sa.ass;
		addAll(sa);
	}
	
	public SelectedApps( Collection<App> list, AppStore ac ) {
		super (new ArrayList (new LinkedHashSet<>(list) ) );
		exemplar = list.iterator().next();
		this.ass = ac;
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
			App up = a.getUp(ass);
			if (up != null)
				ups.add(up);
		}
		SelectedApps sa = new SelectedApps(ass);
		for (App a : ups)
			sa.add( a );
		
		return sa;
	}
	
	private Map<String, SelectedApps> findDown () {
		MultiMap<String, App> as = new MultiMap<>();
		
		for (App a : this) 
			as.putAll( a.getDown(ass) );
		
		Map<String, SelectedApps> out = new LinkedHashMap<>();
		for (String name : as.keySet())
			out.put( name, new SelectedApps(as.get( name ), ass ) );
			
		return out;
	}
	
	public void computeAll( Runnable globalUpdate, ProgressMonitor m ) {
		
		MultiMap<Integer, App> todo = new MultiMap<>();
		int i = NetInfo.evaluationOrder.indexOf( get(0).getClass() );
		todo.putAll( i , this );
		
		App.computeWithChildren( ass, i, todo, globalUpdate );
	}
	
	public JPanel createUI( Runnable update_ ) {

		Runnable update = new Runnable() {
			@Override
			public void run() {
				
				if (exemplar instanceof BlockApp)
					for (App building : exemplar.getDown(ass).valueList())
						building.isDirty = true;
				else
					for (App a : SelectedApps.this)
						a.markDirty(ass);
				
				update_.run();
			}
		};
		
		JPanel top = new JPanel(new ListDownLayout() );
		JPanel main = new JPanel(new BorderLayout() );
		
		JPanel options = new JPanel();

		JPanel countPanel = new JPanel(new BorderLayout() );
		
		JLabel jl = new JLabel( exemplar.name +" ("+size()+" selected)");
				jl.setFont(jl.getFont().deriveFont(jl.getFont().getStyle() | Font.BOLD));

		countPanel.add( jl, BorderLayout.CENTER );
		
		JButton all = new JButton ("all");
		all.addActionListener( e -> TweedFrame.instance.tweed.frame.setGenUI( findAll( exemplar.getClass() ).createUI( update ) ) );
		countPanel.add(all, BorderLayout.EAST);
		
		top.add( countPanel, BorderLayout.NORTH );
		
		
//		SelectedApps ups = findUp();
//		Map<String, SelectedApps> downs = findDown();
		
		JPanel nets = new JPanel(new GridLayout(1, NetInfo.evaluationOrder.size() ) );
		
		int currentIndex = NetInfo.evaluationOrder.indexOf( exemplar.getClass() );
		
		for (Class<? extends App> k : NetInfo.evaluationOrder) {
			
			NetInfo target = NetInfo.get( k );
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
				j.setBorder( new LineBorder( Color.magenta, 3 ) );
			}
			
			j.setToolTipText( target.name + (sa == null ? "" : ("(" +sa.size()+")") ) );
			j.setEnabled( target != exemplar.getNetInfo() && sa != null);
			
			nets.add(j);
			
			SelectedApps sa_ = sa;
			
			j.addActionListener( new ActionListener() {
				@Override
				public void actionPerformed( ActionEvent e ) {
					TweedFrame.instance.tweed.frame.setGenUI( sa_.createUI( update ) );
				}
			} );
			
			
		}
		
		top.add(nets);
		
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
		
//		switch (appMode) {
//		default:
//			exemplar.createNetUI ( update, this );
//			break;
//		case Net:
			out.add( createDistEditor(update) );
//			break;
//		}
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
				if (a.getUp(ass) != null)
					ups.add(a.getUp(ass));
			
			if (ups.isEmpty())
				return current;
			
			current = ups;
		}
	}

	private SelectedApps findAll( Class<? extends App> k ) {
		return findDown (k, new SelectedApps(findRoots(), ass));
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
	
	private Component createDistEditor( Runnable update ) {
		
		JPanel out = new JPanel( new BorderLayout() );
		JPanel options = new JPanel();
		
		JPanel north = new JPanel( new ListDownLayout() );
		
		north.add( exemplar.createNetUI( update, SelectedApps.this ) );
		
		if ( exemplar.appMode == AppMode.Net ) {
			
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
