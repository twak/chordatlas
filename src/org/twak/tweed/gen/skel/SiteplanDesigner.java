package org.twak.tweed.gen.skel;

import java.util.ArrayList;
import java.util.List;

import javax.swing.WindowConstants;
import javax.vecmath.Point2d;

import org.geotools.util.WeakHashSet;
import org.twak.camp.Output;
import org.twak.camp.Output.Face;
import org.twak.camp.Output.SharedEdge;
import org.twak.camp.Skeleton;
import org.twak.camp.ui.Bar;
import org.twak.siteplan.campskeleton.PlanSkeleton;
import org.twak.siteplan.campskeleton.Profile;
import org.twak.siteplan.campskeleton.Siteplan;
import org.twak.tweed.TweedSettings;
import org.twak.tweed.gen.SuperEdge;
import org.twak.tweed.gen.SuperFace;
import org.twak.utils.collections.Loop;
import org.twak.utils.geom.HalfMesh2.HalfEdge;
import org.twak.utils.ui.Colourz;
import org.twak.utils.ui.Plot;
import org.twak.utils.ui.Rainbow;
import org.twak.viewTrace.facades.CGAMini;
import org.twak.viewTrace.facades.GreebleHelper;
import org.twak.viewTrace.franken.App.TextureMode;
import org.twak.viewTrace.franken.FacadeTexApp;

public class SiteplanDesigner {

	public SiteplanDesigner( SuperFace sf, SkelGen sg ) {

		sg.closeSitePlan();
		Plot.closeLast();

		if ( !TweedSettings.settings.experimentalInteractiveTextures )
			for ( HalfEdge he : sf ) {
				SuperEdge ee = (SuperEdge) he;
				if ( ee.toEdit != null )
					ee.toEdit.facadeTexApp.appMode = TextureMode.Off;
			}

		sf.skel.plan.setAllowAddLoop(false);
		sf.skel.plan.setAllowRemoveLoop(false);
		
		SkelGen.siteplan = new Siteplan( sf.skel.plan, false ) {

			public void show( Output output, Skeleton threadKey ) {

				super.show( output, threadKey );

				sg.tweed.enqueue( new Runnable() {

					@Override
					public void run() {

						sg.removeGeometryFor( sf );
						sg.tweed.frame.setGenUI( null ); // current selection is invalid
						sf.skel = (PlanSkeleton) threadKey;

						for ( Face f : sf.skel.output.faces.values() ) {

							WallTag wt = ( (WallTag) GreebleHelper.getTag( f.profile, WallTag.class ) );

							if ( wt != null ) {

								SETag set = (SETag) GreebleHelper.getTag( f.plan, SETag.class );
								if ( set != null ) // created by siteplan --> set correct face
									wt.miniFacade.facadeTexApp.parent = (SuperFace) set.se.face;
								
								
								if (f.parent == null) {
									// update profile to halfmesh
									Bar bar = sf.skel.columnProperties.get( f.edge ).defBar;
									Profile profile =sf.skel.plan.profiles.get( bar );
									wt.occlusionID.prof = SkelGen.toProf( profile );

									// update vertex locations in halfmesh
									SharedEdge se = f.definingSE.iterator().next();
									wt.occlusionID.end.set (se.start.x, se.start.y);
									wt.occlusionID.start.set (se.end.x, se.end.y);
								}
							}
						}

						sf.skel.output.addNonSkeletonSharedEdges( new RoofTag( Colourz.toF4( sf.mr.roofTexApp.color ) ) );
						sf.mr.setOutline( sf.skel.output );

						sg.setSkel( (PlanSkeleton) threadKey, sf );

						if ( TweedSettings.settings.experimentalInteractiveTextures )
							sg.updateTextureThenGeom( sf.buildingApp );
					}
				} );
			}

			public void addedBar( Bar bar ) {

				
				SETag oldTag = (SETag) bar.tags.iterator().next();

				bar.tags.clear();
				SuperEdge se = new SuperEdge( bar.end,  bar.start, null );

				SETag tag = new SETag( se, sf );
				tag.color = Rainbow.random();
				tag.name = Math.random() + "";
				bar.tags.add( tag );

				List<Point2d> defpts = new ArrayList<>();
				defpts.add( new Point2d( 0, 0 ) );
				defpts.add( new Point2d( 0, -7 ) );
				defpts.add( new Point2d( 5, -12 ) );

				Profile profile = new Profile( defpts );
				sg.tagWalls( sf, profile, se, bar.start, bar.end );

				FacadeTexApp mfa = se.toEdit.facadeTexApp;

				if ( oldTag != null )
					mfa.parent = oldTag.sf;

				if ( TweedSettings.settings.experimentalInteractiveTextures ) {

					mfa.appMode = TextureMode.Net;
					se.toEdit.height = 5;
					se.toEdit.featureGen = new CGAMini( se.toEdit );

					if ( oldTag != null )
						oldTag.sf.insert( se ); //!
				}

				plan.addLoop( profile.points.get( 0 ), plan.root, profile );
				
				
				plan.profiles.put( bar, profile );
			};
		};

		SkelGen.siteplan.setVisible( true );
		SkelGen.siteplan.setDefaultCloseOperation( WindowConstants.DISPOSE_ON_CLOSE );
	}
}
