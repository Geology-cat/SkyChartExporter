// ----------------------------------------------------------------------------
// SkyChartExporter - SCE-Dialog.jsh
//
// メインダイアログとプレビューダイアログ。
// 言語を切り替えると設定を保存してダイアログを作り直す（restart = true）。
// ----------------------------------------------------------------------------

#ifndef __SCE_Dialog_jsh
#define __SCE_Dialog_jsh

class ScePreviewDialog extends Dialog
{
   constructor( bmp, lang )
   {
      super();
      this.windowTitle = sceTr( "previewTitle", lang );
      this.bmp = bmp;

      this.view = new Control( this );
      this.view.setFixedSize( bmp.width, bmp.height );
      this.view.onPaint = function( x0, y0, x1, y1 )
      {
         let g = new Graphics( this );
         g.drawBitmap( 0, 0, this.dialog.bmp );
         g.end();
      };

      this.ok_Button = new PushButton( this );
      this.ok_Button.text = sceTr( "close", lang );
      this.ok_Button.onClick = () => this.ok();

      let buttons = new HorizontalSizer;
      buttons.addStretch();
      buttons.add( this.ok_Button );

      this.sizer = new VerticalSizer;
      this.sizer.margin = 8;
      this.sizer.spacing = 8;
      this.sizer.add( this.view );
      this.sizer.add( buttons );
      this.adjustToContents();
      this.setFixedSize();
   }
}

// ----------------------------------------------------------------------------

class SceDialog extends Dialog
{
   constructor( exporter, initialView )
   {
      super();
      this.exporter = exporter;
      this.restart = false;
      let o = exporter.opts;
      let L = o.lang;
      let T = k => sceTr( k, L );
      this.T = T;
      this.L = L;

      this.windowTitle = T( "dialogTitle" );
      let labelW = this.font.width( (L == "ja") ? "MMMMMMMMMMMM" : "MMMMMMMMMMMMMM" );
      let editW = this.font.width( "MMMMMMM" );
      this.labelW = labelW;

      // --- 共通ヘルパー ---
      let label = ( text, w = labelW ) =>
      {
         let l = new Label( this );
         l.text = text;
         l.textAlignment = TextAlignment.Right | TextAlignment.VertCenter;
         l.minWidth = w;
         return l;
      };
      let row = ( text, ctrl, stretch = true, tip ) =>
      {
         let s = new HorizontalSizer;
         s.spacing = 6;
         s.add( label( text ) );
         s.add( ctrl, stretch ? 100 : 0 );
         if ( !stretch )
            s.addStretch();
         if ( tip )
            ctrl.toolTip = tip;
         return s;
      };
      let combo = ( items, index ) =>
      {
         let c = new ComboBox( this );
         for ( let it of items )
            c.addItem( it );
         c.currentItem = Math.max( 0, index );
         return c;
      };
      let check = ( text, checked ) =>
      {
         let c = new CheckBox( this );
         c.text = text;
         c.checked = checked;
         return c;
      };
      let numeric = ( text, min, max, prec, value, w = labelW ) =>
      {
         let n = new NumericEdit( this );
         n.label.text = text;
         n.label.minWidth = w;
         n.real = prec > 0;
         n.setRange( min, max );
         n.setPrecision( prec );
         n.edit.setFixedWidth( editW );
         n.setValue( value );
         return n;
      };
      let group = ( title ) =>
      {
         let g = new GroupBox( this );
         g.title = title;
         g.sizer = new VerticalSizer;
         g.sizer.margin = 6;
         g.sizer.spacing = 4;
         return g;
      };

      // --- 説明と言語 ---
      this.help_Label = new Label( this );
      this.help_Label.text = T( "help" );
      this.help_Label.wordWrapping = true;
      this.help_Label.frameStyle = FrameStyle.Box;
      this.help_Label.margin = 6;

      this.lang_Combo = combo( [ "日本語", "English" ], (L == "en") ? 1 : 0 );
      this.lang_Combo.onItemSelected = ( index ) =>
      {
         let newLang = (index == 1) ? "en" : "ja";
         if ( newLang != this.exporter.opts.lang )
         {
            this.collect();
            this.exporter.opts.lang = newLang;
            this.exporter.opts.save();
            this.restart = true;
            this.ok();
         }
      };
      let langSizer = new HorizontalSizer;
      langSizer.spacing = 6;
      langSizer.addStretch();
      langSizer.add( label( T( "language" ), 0 ) );
      langSizer.add( this.lang_Combo );

      // --- 対象画像 ---
      this.view_List = new ViewList( this );
      this.view_List.getMainViews();
      if ( initialView && !initialView.isNull )
         this.view_List.currentView = initialView;
      this.view_List.onViewSelected = () =>
      {
         // 手入力の焦点距離・画素サイズは画像ごとの値なので、画像を切り替えたら戻す
         if ( this.focal_Num )
            this.focal_Num.setValue( 0 );
         if ( this.pixel_Num )
            this.pixel_Num.setValue( 0 );
         this.updateImageInfo();
      };

      // --- ドキュメント ---
      let gDoc = group( T( "grpDocument" ) );
      gDoc.sizer.add( row( T( "targetImage" ), this.view_List ) );
      this.title_Edit = new Edit( this );
      this.title_Edit.text = o.title;
      gDoc.sizer.add( row( T( "title" ), this.title_Edit, true, T( "titleTip" ) ) );
      this.observer_Edit = new Edit( this );
      this.observer_Edit.text = o.observer;
      gDoc.sizer.add( row( T( "observer" ), this.observer_Edit ) );
      this.site_Edit = new Edit( this );
      this.site_Edit.text = o.site;
      gDoc.sizer.add( row( T( "site" ), this.site_Edit ) );

      this.page_Combo = combo( SCE_PAGE_KEYS.map( k => SCE_PAGE_SIZES[k][L] ), SCE_PAGE_KEYS.indexOf( o.pageSize ) );
      gDoc.sizer.add( row( T( "pageSize" ), this.page_Combo, false ) );
      this.theme_Combo = combo( [ T( "themeLight" ), T( "themeDark" ) ], (o.theme == "dark") ? 1 : 0 );
      gDoc.sizer.add( row( T( "theme" ), this.theme_Combo, false ) );

      this.fontFamilies = [];
      try
      {
         this.fontFamilies = Font.families( WritingSystem.Japanese ).filter( f => !f.startsWith( "." ) );
      }
      catch ( x )
      {
      }
      this.font_Combo = combo( [ T( "fontAuto" ) ].concat( this.fontFamilies ),
                               o.fontFamily ? this.fontFamilies.indexOf( o.fontFamily ) + 1 : 0 );
      gDoc.sizer.add( row( T( "font" ), this.font_Combo, false ) );

      // --- 画像と圧縮 ---
      let gImg = group( T( "grpImage" ) );
      this.stretchKeys = [ "auto", "none", "stf", "autostf" ];
      this.stretch_Combo = combo( [ T( "stretchAuto" ), T( "stretchNone" ), T( "stretchSTF" ), T( "stretchAutoSTF" ) ],
                                  this.stretchKeys.indexOf( o.stretch ) );
      gImg.sizer.add( row( T( "stretch" ), this.stretch_Combo, false ) );
      this.dpi_Num = numeric( T( "imageDpi" ), 72, 400, 0, o.imageDpi );
      gImg.sizer.add( this.dpi_Num );
      this.autoCompress_Check = check( T( "autoCompress" ), o.autoCompress );
      let acs = new HorizontalSizer;
      acs.addSpacing( labelW + 6 );
      acs.add( this.autoCompress_Check );
      acs.addStretch();
      gImg.sizer.add( acs );
      this.maxSize_Num = numeric( T( "maxSize" ), 0.3, 50, 1, o.maxSizeMB );
      gImg.sizer.add( this.maxSize_Num );
      this.focal_Num = numeric( T( "focalOverride" ), 0, 20000, 0, o.focalOverride );
      gImg.sizer.add( this.focal_Num );
      this.pixel_Num = numeric( T( "pixelOverride" ), 0, 50, 2, o.pixelOverride );
      gImg.sizer.add( this.pixel_Num );
      this.focal_Num.onValueUpdated = () => this.updateImageInfo();
      this.pixel_Num.onValueUpdated = () => this.updateImageInfo();
      // 検出した画像情報（カメラ・画素サイズ・焦点距離）
      this.imageInfo_Label = new Label( this );
      this.imageInfo_Label.wordWrapping = true;
      this.imageInfo_Label.useRichText = false;
      this.imageInfo_Label.frameStyle = FrameStyle.Box;
      this.imageInfo_Label.margin = 4;
      gImg.sizer.add( this.imageInfo_Label );

      // --- アノテーション ---
      let gAnn = group( T( "grpAnnot" ) );
      this.presetKeys = SCE_PRESET_KEYS.concat( [ "custom" ] );
      this.preset_Combo = combo( [ T( "lvNone" ), T( "lvMinimal" ), T( "lvStandard" ), T( "lvDetailed" ), T( "lvAll" ), T( "lvCustom" ) ],
                                 this.presetKeys.indexOf( o.matchingPreset() ) );
      this.preset_Combo.onItemSelected = ( index ) =>
      {
         let key = this.presetKeys[index];
         if ( key != "custom" )
         {
            let tmp = new SceOptions();
            tmp.applyPreset( key );
            this.setAnnotationControls( tmp );
         }
      };
      gAnn.sizer.add( row( T( "annotLevel" ), this.preset_Combo, false ) );

      let mk = ( key, textKey ) =>
      {
         let c = check( T( textKey ), o[key] );
         c.onCheck = () => this.updatePresetCombo();
         return c;
      };
      this.aMessier_Check = mk( "aMessier", "aMessier" );
      this.aCaldwell_Check = mk( "aCaldwell", "aCaldwell" );
      this.aNamed_Check = mk( "aNamed", "aNamed" );
      this.aNGC_Check = mk( "aNGC", "aNGC" );
      this.aSNR_Check = mk( "aSNR", "aSNR" );
      this.aStars_Check = mk( "aStars", "aStars" );
      this.aConstLines_Check = mk( "aConstLines", "aConstLines" );
      this.aConstBorders_Check = mk( "aConstBorders", "aConstBorders" );
      this.aConstNames_Check = mk( "aConstNames", "aConstNames" );
      this.aGrid_Check = mk( "aGrid", "aGrid" );
      this.aCompass_Check = mk( "aCompass", "aCompass" );
      this.aBrightNeb_Check = mk( "aBrightNeb", "aBrightNeb" );
      this.aDarkNeb_Check = mk( "aDarkNeb", "aDarkNeb" );
      this.aPN_Check = mk( "aPN", "aPN" );
      this.aClusters_Check = mk( "aClusters", "aClusters" );
      this.aGalaxies_Check = mk( "aGalaxies", "aGalaxies" );
      this.aGalaxyClusters_Check = mk( "aGalaxyClusters", "aGalaxyClusters" );
      this.aBrightNeb_Check.toolTip = T( "aBrightNebTip" );
      this.aDarkNeb_Check.toolTip = T( "aDarkNebTip" );
      this.aPN_Check.toolTip = T( "aPNTip" );
      this.aClusters_Check.toolTip = T( "aClustersTip" );
      this.aGalaxies_Check.toolTip = T( "aGalaxiesTip" );
      this.aGalaxyClusters_Check.toolTip = T( "aGalaxyClustersTip" );
      this.aLabelNames_Check = check( T( "aLabelNames" ), o.aLabelNames );

      let grid2 = ( a, b ) =>
      {
         let s = new HorizontalSizer;
         s.spacing = 12;
         s.addSpacing( 8 );
         s.add( a );
         if ( b )
            s.add( b );
         s.addStretch();
         return s;
      };
      // 2列に並べる
      let colA = new VerticalSizer, colB = new VerticalSizer, colC = new VerticalSizer;
      colA.spacing = colB.spacing = colC.spacing = 3;
      for ( let c of [ this.aMessier_Check, this.aCaldwell_Check, this.aNamed_Check, this.aNGC_Check, this.aBrightNeb_Check,
                       this.aDarkNeb_Check ] )
         colA.add( c );
      for ( let c of [ this.aPN_Check, this.aSNR_Check, this.aClusters_Check, this.aGalaxies_Check, this.aGalaxyClusters_Check,
                       this.aStars_Check ] )
         colB.add( c );
      for ( let c of [ this.aConstLines_Check, this.aConstBorders_Check, this.aConstNames_Check, this.aGrid_Check,
                       this.aCompass_Check, this.aLabelNames_Check ] )
         colC.add( c );
      let cols = new HorizontalSizer;
      cols.spacing = 16;
      cols.addSpacing( 8 );
      cols.add( colA );
      cols.add( colB );
      cols.add( colC );
      cols.addStretch();
      gAnn.sizer.add( cols );

      this.aNGCMag_Num = numeric( T( "aNGCMag" ), 6, 20, 1, o.aNGCMag );
      this.aNGCMag_Num.onValueUpdated = () => this.updatePresetCombo();
      gAnn.sizer.add( this.aNGCMag_Num );
      this.aGalaxyMag_Num = numeric( T( "aGalaxyMag" ), 6, 20, 1, o.aGalaxyMag );
      this.aGalaxyMag_Num.onValueUpdated = () => this.updatePresetCombo();
      gAnn.sizer.add( this.aGalaxyMag_Num );
      this.aStarMag_Num = numeric( T( "aStarMag" ), 0, 10, 1, o.aStarMag );
      this.aStarMag_Num.onValueUpdated = () => this.updatePresetCombo();
      gAnn.sizer.add( this.aStarMag_Num );
      this.aLabelSize_Num = numeric( T( "aLabelSize" ), 4, 14, 1, o.aLabelSize );
      gAnn.sizer.add( this.aLabelSize_Num );

      // --- 星図 ---
      let gChart = group( T( "grpChart" ) );
      this.fovKeys = [ "auto", "factor", "fixed" ];
      this.fov_Combo = combo( [ T( "fovAuto" ), T( "fovFactor" ), T( "fovFixed" ) ], this.fovKeys.indexOf( o.chartFovMode ) );
      this.fov_Combo.onItemSelected = () => this.updateFovControls();
      gChart.sizer.add( row( T( "chartFov" ), this.fov_Combo, false ) );
      this.fovFactor_Num = numeric( T( "chartFactor" ), 1.2, 200, 1, o.chartFovFactor );
      gChart.sizer.add( this.fovFactor_Num );
      this.fovDeg_Num = numeric( T( "chartDeg" ), 0.5, 150, 1, o.chartFovDeg );
      gChart.sizer.add( this.fovDeg_Num );
      this.chartStarMag_Num = numeric( T( "chartStarMag" ), 0, 8, 1, o.chartStarMag );
      gChart.sizer.add( this.chartStarMag_Num );
      this.cDSO_Check = check( T( "cDSO" ), o.chartDSO );
      this.cConstLines_Check = check( T( "cConstLines" ), o.chartConstLines );
      this.cConstBorders_Check = check( T( "cConstBorders" ), o.chartConstBorders );
      this.cConstNames_Check = check( T( "cConstNames" ), o.chartConstNames );
      this.cGrid_Check = check( T( "cGrid" ), o.chartGrid );
      gChart.sizer.add( grid2( this.cDSO_Check, this.cGrid_Check ) );
      gChart.sizer.add( grid2( this.cConstLines_Check, this.cConstBorders_Check ) );
      this.useGaia_Check = check( T( "useGaia" ), o.useGaia );
      gChart.sizer.add( grid2( this.cConstNames_Check ) );
      gChart.sizer.add( grid2( this.useGaia_Check ) );
      this.updateFovControls();

      // --- 一覧 ---
      let gList = group( T( "grpList" ) );
      this.listMax_Num = numeric( T( "listMax" ), 0, 200, 0, o.listMax );
      gList.sizer.add( this.listMax_Num );
      this.listStars_Check = check( T( "listStars" ), o.listStars );
      gList.sizer.add( grid2( this.listStars_Check ) );

      // --- メモ ---
      let gMemo = group( T( "grpMemo" ) );
      this.memo_Text = new TextBox( this );
      this.memo_Text.text = o.memo;
      this.memo_Text.setScaledMinHeight( 56 );

      // --- カタログ ---
      let gCat = group( T( "grpCatalog" ) );
      let dirRow = ( edit, tip, onBrowse ) =>
      {
         let b = new ToolButton( this );
         b.text = T( "browse" );
         b.onClick = onBrowse;
         let s = new HorizontalSizer;
         s.spacing = 4;
         s.add( edit, 100 );
         s.add( b );
         edit.toolTip = tip;
         return s;
      };
      let browseDir = ( edit ) => () =>
      {
         let gdd = new GetDirectoryDialog;
         gdd.caption = T( "grpCatalog" );
         if ( edit.text.length > 0 )
            gdd.initialPath = edit.text;
         if ( gdd.execute() )
         {
            edit.text = gdd.directory;
            this.updateCatalogStatus();
         }
      };
      this.useStellarium_Check = check( T( "useStellarium" ), o.useStellarium );
      this.useStellarium_Check.onCheck = () => this.updateCatalogStatus();
      gCat.sizer.add( this.useStellarium_Check );
      let l1 = new Label( this );
      l1.text = T( "stellariumDir" );
      gCat.sizer.add( l1 );
      this.stellariumDir_Edit = new Edit( this );
      this.stellariumDir_Edit.text = o.stellariumDir;
      this.stellariumDir_Edit.onEditCompleted = () => this.updateCatalogStatus();
      gCat.sizer.add( dirRow( this.stellariumDir_Edit, T( "stellariumDirTip" ), browseDir( this.stellariumDir_Edit ) ) );
      this.stelStatus_Label = new Label( this );
      this.stelStatus_Label.wordWrapping = true;
      gCat.sizer.add( this.stelStatus_Label );
      let l2 = new Label( this );
      l2.text = T( "gaiaDir" );
      gCat.sizer.add( l2 );
      this.gaiaDir_Edit = new Edit( this );
      this.gaiaDir_Edit.text = o.gaiaDir;
      gCat.sizer.add( dirRow( this.gaiaDir_Edit, T( "gaiaDirTip" ), browseDir( this.gaiaDir_Edit ) ) );
      let note = new Label( this );
      note.text = T( "catNeedsStel" );
      note.wordWrapping = true;
      gCat.sizer.add( note );
      this.updateCatalogStatus();
      gMemo.sizer.add( this.memo_Text, 100 );

      // --- ボタン ---
      this.preview_Button = new PushButton( this );
      this.preview_Button.text = T( "preview" );
      this.preview_Button.onClick = () => this.doPreview();
      this.export_Button = new PushButton( this );
      this.export_Button.text = T( "exportPdf" );
      this.export_Button.defaultButton = true;
      this.export_Button.onClick = () => this.doExport();
      this.close_Button = new PushButton( this );
      this.close_Button.text = T( "close" );
      this.close_Button.onClick = () =>
      {
         this.collect();
         this.ok();
      };
      let buttons = new HorizontalSizer;
      buttons.spacing = 8;
      buttons.add( this.preview_Button );
      buttons.addStretch();
      buttons.add( this.export_Button );
      buttons.add( this.close_Button );

      // --- 全体の配置（左: ドキュメント・画像 / 中: 注記 / 右: 星図・一覧・カタログ / 下: メモ） ---
      let col1 = new VerticalSizer;
      col1.spacing = 6;
      col1.add( gDoc );
      col1.add( gImg );
      col1.addStretch();
      let col2 = new VerticalSizer;
      col2.spacing = 6;
      col2.add( gAnn );
      col2.add( gList );
      col2.addStretch();
      let col3 = new VerticalSizer;
      col3.spacing = 6;
      col3.add( gChart );
      col3.add( gCat );
      col3.addStretch();
      let body = new HorizontalSizer;
      body.spacing = 8;
      body.add( col1, 34 );
      body.add( col2, 33 );
      body.add( col3, 33 );

      this.sizer = new VerticalSizer;
      this.sizer.margin = 8;
      this.sizer.spacing = 8;
      this.sizer.add( langSizer );
      this.sizer.add( this.help_Label );
      this.sizer.add( body );
      this.sizer.add( gMemo, 100 );
      this.sizer.add( buttons );

      this.userResizable = true;
      this.adjustToContents();
      this.updateImageInfo();
   }

   // Stellarium の検出状況を表示する
   updateCatalogStatus()
   {
      let T = this.T;
      if ( !this.useStellarium_Check.checked )
      {
         this.stelStatus_Label.text = "";
         return;
      }
      let dir = sceFindStellariumDir( this.stellariumDir_Edit.text.trim() );
      this.stelStatus_Label.text = dir ? sceFmt( T( "stelFound" ), dir ) : T( "stelNotFound" );
   }

   // 選択中の画像のカメラ・画素サイズ・焦点距離を表示する
   updateImageInfo()
   {
      let T = this.T;
      try
      {
         let v = this.view_List.currentView;
         if ( !v || v.isNull )
         {
            this.imageInfo_Label.text = T( "noImage" );
            return;
         }
         let w = v.window;
         if ( !w.hasAstrometricSolution )
         {
            this.imageInfo_Label.text = T( "noSolutionShort" );
            return;
         }
         let info = new SceImageInfo( w );
         let tmp = new SceOptions();
         tmp.cameraPixelSizes = this.exporter.opts.cameraPixelSizes;
         tmp.pixelOverride = this.pixel_Num.value;
         tmp.focalOverride = this.focal_Num.value;
         let f = info.resolveFocal( tmp );
         let lines = [ sceFmt( T( "infoScale" ), info.scaleArcsec ) ];
         lines.push( info.camera ? sceFmt( T( "infoCamera" ), info.camera ) : T( "infoNoCamera" ) );
         if ( f.px > 0 )
         {
            let src = { header: "srcHeader", camera: "srcCamera", remembered: "srcRemembered", manual: "srcManual" }[f.pxSource];
            lines.push( sceFmt( T( "infoPixel" ), "" + Math.round( f.px*100 )/100, T( src ) ) );
         }
         else
            lines.push( T( "infoNoPixel" ) );
         if ( f.source == "fov35" )
            lines.push( sceFmt( T( "infoFocalApprox" ), f.value ) );
         else if ( f.value > 0 )
            lines.push( sceFmt( T( "infoFocal" ), f.value ) );
         this.imageInfo_Label.text = lines.join( "\n" );
      }
      catch ( x )
      {
         this.imageInfo_Label.text = "" + x;
      }
   }

   setAnnotationControls( src )
   {
      this.aMessier_Check.checked = src.aMessier;
      this.aCaldwell_Check.checked = src.aCaldwell;
      this.aNamed_Check.checked = src.aNamed;
      this.aNGC_Check.checked = src.aNGC;
      this.aSNR_Check.checked = src.aSNR;
      this.aStars_Check.checked = src.aStars;
      this.aConstLines_Check.checked = src.aConstLines;
      this.aConstBorders_Check.checked = src.aConstBorders;
      this.aConstNames_Check.checked = src.aConstNames;
      this.aGrid_Check.checked = src.aGrid;
      this.aCompass_Check.checked = src.aCompass;
      this.aNGCMag_Num.setValue( src.aNGCMag );
      this.aStarMag_Num.setValue( src.aStarMag );
      this.aBrightNeb_Check.checked = src.aBrightNeb;
      this.aDarkNeb_Check.checked = src.aDarkNeb;
      this.aPN_Check.checked = src.aPN;
      this.aClusters_Check.checked = src.aClusters;
      this.aGalaxies_Check.checked = src.aGalaxies;
      this.aGalaxyClusters_Check.checked = src.aGalaxyClusters;
      this.aGalaxyMag_Num.setValue( src.aGalaxyMag );
   }

   updatePresetCombo()
   {
      let tmp = new SceOptions();
      this.collectAnnotation( tmp );
      this.preset_Combo.currentItem = this.presetKeys.indexOf( tmp.matchingPreset() );
   }

   updateFovControls()
   {
      let key = this.fovKeys[this.fov_Combo.currentItem];
      this.fovFactor_Num.enabled = key == "factor";
      this.fovDeg_Num.enabled = key == "fixed";
   }

   collectAnnotation( o )
   {
      o.aMessier = this.aMessier_Check.checked;
      o.aCaldwell = this.aCaldwell_Check.checked;
      o.aNamed = this.aNamed_Check.checked;
      o.aNGC = this.aNGC_Check.checked;
      o.aSNR = this.aSNR_Check.checked;
      o.aStars = this.aStars_Check.checked;
      o.aConstLines = this.aConstLines_Check.checked;
      o.aConstBorders = this.aConstBorders_Check.checked;
      o.aConstNames = this.aConstNames_Check.checked;
      o.aGrid = this.aGrid_Check.checked;
      o.aCompass = this.aCompass_Check.checked;
      o.aNGCMag = this.aNGCMag_Num.value;
      o.aStarMag = this.aStarMag_Num.value;
      o.aBrightNeb = this.aBrightNeb_Check.checked;
      o.aDarkNeb = this.aDarkNeb_Check.checked;
      o.aPN = this.aPN_Check.checked;
      o.aClusters = this.aClusters_Check.checked;
      o.aGalaxies = this.aGalaxies_Check.checked;
      o.aGalaxyClusters = this.aGalaxyClusters_Check.checked;
      o.aGalaxyMag = this.aGalaxyMag_Num.value;
   }

   // コントロールの値を設定に反映する
   collect()
   {
      let o = this.exporter.opts;
      o.title = this.title_Edit.text.trim();
      o.observer = this.observer_Edit.text.trim();
      o.site = this.site_Edit.text.trim();
      o.pageSize = SCE_PAGE_KEYS[this.page_Combo.currentItem];
      o.theme = (this.theme_Combo.currentItem == 1) ? "dark" : "light";
      o.fontFamily = (this.font_Combo.currentItem > 0) ? this.fontFamilies[this.font_Combo.currentItem - 1] : "";
      o.stretch = this.stretchKeys[this.stretch_Combo.currentItem];
      o.imageDpi = Math.round( this.dpi_Num.value );
      o.autoCompress = this.autoCompress_Check.checked;
      o.maxSizeMB = this.maxSize_Num.value;
      o.focalOverride = this.focal_Num.value;
      o.pixelOverride = this.pixel_Num.value;
      this.collectAnnotation( o );
      o.annotPreset = o.matchingPreset();
      o.aLabelNames = this.aLabelNames_Check.checked;
      o.aLabelSize = this.aLabelSize_Num.value;
      o.chartFovMode = this.fovKeys[this.fov_Combo.currentItem];
      o.chartFovFactor = this.fovFactor_Num.value;
      o.chartFovDeg = this.fovDeg_Num.value;
      o.chartStarMag = this.chartStarMag_Num.value;
      o.chartDSO = this.cDSO_Check.checked;
      o.chartConstLines = this.cConstLines_Check.checked;
      o.chartConstBorders = this.cConstBorders_Check.checked;
      o.chartConstNames = this.cConstNames_Check.checked;
      o.chartGrid = this.cGrid_Check.checked;
      o.listMax = Math.round( this.listMax_Num.value );
      o.listStars = this.listStars_Check.checked;
      o.useGaia = this.useGaia_Check.checked;
      o.useStellarium = this.useStellarium_Check.checked;
      o.stellariumDir = this.stellariumDir_Edit.text.trim();
      o.gaiaDir = this.gaiaDir_Edit.text.trim();
      o.memo = this.memo_Text.text;
   }

   targetWindow()
   {
      let v = this.view_List.currentView;
      if ( !v || v.isNull )
         throw new Error( this.T( "noImage" ) );
      return v.window;
   }

   showError( x )
   {
      console.criticalln( "SkyChartExporter: " + x );
      (new MessageBox( "" + (x.message || x), "SkyChartExporter", StdIcon.Error, StdButton.Ok )).execute();
   }

   doPreview()
   {
      this.collect();
      try
      {
         let w = this.targetWindow();
         console.show();
         console.writeln( "<end><cbr>SkyChartExporter: " + this.T( "working" ) );
         processEvents();
         let width = Math.min( 2400, Math.round( this.logicalPixelsToPhysical( 1100 ) ) );
         let bmp = this.exporter.previewBitmap( w, width );
         (new ScePreviewDialog( bmp, this.L )).execute();
      }
      catch ( x )
      {
         this.showError( x );
      }
   }

   doExport()
   {
      this.collect();
      let o = this.exporter.opts;
      try
      {
         let w = this.targetWindow();
         let sfd = new SaveFileDialog;
         sfd.caption = this.T( "saveTitle" );
         sfd.filters = [ [ "PDF", "*.pdf" ] ];
         let base = (w.filePath ? File.extractName( w.filePath ) : w.mainView.id) + "_skychart.pdf";
         let dir = o.outputDir;
         if ( !dir || !File.directoryExists( dir ) )
            dir = w.filePath ? File.extractDrive( w.filePath ) + File.extractDirectory( w.filePath ) : File.homeDirectory;
         sfd.initialPath = dir + "/" + base;
         if ( !sfd.execute() )
            return;
         let path = sfd.filePath;
         if ( File.extractExtension( path ).toLowerCase() != ".pdf" )
            path += ".pdf";
         o.outputDir = File.extractDrive( path ) + File.extractDirectory( path );
         o.save();

         console.show();
         console.writeln( "<end><cbr>SkyChartExporter: " + this.T( "working" ) );
         processEvents();
         let r = this.exporter.exportPDF( w, path );
         let msg = this.T( "exported" ) + "\n\n" + path + "\n" +
                   format( "%.2f MB, %d px (%.0f dpi)", r.size/1048576, r.photoPx, r.dpi );
         if ( r.overTarget )
            msg += "\n\n" + this.T( "sizeOver" );
         console.writeln( msg );
         (new MessageBox( msg, "SkyChartExporter", r.overTarget ? StdIcon.Warning : StdIcon.Information, StdButton.Ok )).execute();
      }
      catch ( x )
      {
         this.showError( x );
      }
   }
}

#endif   // __SCE_Dialog_jsh
