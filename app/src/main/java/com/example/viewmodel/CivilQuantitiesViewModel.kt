package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.db.ProjectEntity
import com.example.data.db.WorkPlanEntity
import com.example.data.db.WorkPlanPhaseEntity
import com.example.data.db.WorkPlanWithPhases
import com.example.data.repository.ProjectRepository
import com.example.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.roundToInt

enum class AppScreen(val titleAr: String) {
    HOME("الرئيسية"),
    CONCRETE("الخرسانات"),
    STEEL("حديد التسليح"),
    MASONRY("المباني والطوب"),
    FINISHING("التشطيبات والبياض"),
    BLUEPRINT_IMPORT("المخططات والملفات الهندسية"),
    SPEC_ANALYZER("كراسات المواصفات والمستندات"),
    WORK_PLAN("خطة العمل والجدول الزمني"),
    COMMUNITY("مجتمع المهندسين"),
    BOQ("جدول الكميات والتكلفة"),
    CODES_GUIDE("الكود والمواصفات"),
    SAVED_PROJECTS("المشاريع المحفوظة"),
    ABOUT_PRIVACY("عن التطبيق والخصوصية")
}

sealed interface BlueprintAnalysisStatus {
    data object Idle : BlueprintAnalysisStatus
    data class Loading(val statusMessage: String) : BlueprintAnalysisStatus
    data object Success : BlueprintAnalysisStatus
    data class Error(val message: String) : BlueprintAnalysisStatus
}

data class BlueprintAggregatedTotals(
    val totalConcreteNetM3: Double = 0.0,
    val totalConcreteGrossM3: Double = 0.0,
    val cementBags50Kg: Double = 0.0,
    val cementTonnes: Double = 0.0,
    val sandM3: Double = 0.0,
    val sandTataTrucks: Double = 0.0,
    val gravelM3: Double = 0.0,
    val gravelTataTrucks: Double = 0.0,
    val waterDrums: Double = 0.0,
    val explicitSteelKg: Double = 0.0,
    val estimatedTotalSteelKg: Double = 0.0,
    val estimatedTotalSteelTonnes: Double = 0.0,
    val equivalent12mBarsD16: Double = 0.0,
    val steelByDiameter: List<RebarDiameterBreakdown> = emptyList(),
    val masonryAreaM2: Double = 0.0,
    val totalBricksCount: Int = 0,
    val totalBricksThousand: Double = 0.0,
    val mortarCementBags: Double = 0.0,
    val mortarSandM3: Double = 0.0,
    val finishingAreaM2: Double = 0.0
)

data class BlueprintAnalyzerUiState(
    val selectedFileKind: EngineeringFileReader.FileKind? = null,
    val selectedFileNames: List<String> = emptyList(),
    val selectedImageUris: List<Uri> = emptyList(),
    val selectedDocUri: Uri? = null,
    val previewBitmaps: List<Bitmap> = emptyList(),
    val pdfPageCount: Int = 0,
    val currentPdfPageIndex: Int = 0,
    val dxfParseResult: DxfParseResult? = null,
    val dxfUnit: CadDrawingUnit = CadDrawingUnit.METERS,
    val scheduleTextContent: String? = null,
    val defaultHeightM: String = "3.0",
    val defaultSlabThicknessM: String = "0.15",
    val defaultMixGrade: ConcreteGrade = ConcreteGrade.C25,
    val defaultBrickType: MasonryEstimator.BrickType = MasonryEstimator.BrickType.SUDANESE_RED_BRICK,
    val defaultSteelDiameter: RebarDiameter = RebarDiameter.D16,
    val wastagePercent: String = "5.0",
    val steelRateKgPerM3: String = "110.0",
    val userContextNote: String = "",
    val analysisStatus: BlueprintAnalysisStatus = BlueprintAnalysisStatus.Idle,
    val report: BlueprintAnalysisReport? = null,
    val aggregatedTotals: BlueprintAggregatedTotals = BlueprintAggregatedTotals()
)

enum class SpecSourceMode(val arLabel: String) {
    DOCUMENT("مستند (Word / PDF / Excel)"),
    IMAGES("صور صفحات الكراسة"),
    PASTED_TEXT("لصق نص الكراسة")
}

data class SpecAggregatedTotals(
    val totalConcreteNetM3: Double = 0.0,
    val totalConcreteGrossM3: Double = 0.0,
    val cementBags50Kg: Double = 0.0,
    val cementTonnes: Double = 0.0,
    val sandM3: Double = 0.0,
    val sandTataTrucks: Double = 0.0,
    val gravelM3: Double = 0.0,
    val gravelTataTrucks: Double = 0.0,
    val waterDrums: Double = 0.0,
    val explicitSteelKg: Double = 0.0,
    val estimatedTotalSteelKg: Double = 0.0,
    val estimatedTotalSteelTonnes: Double = 0.0,
    val steelByDiameter: List<RebarDiameterBreakdown> = emptyList(),
    val masonryAreaM2: Double = 0.0,
    val totalBricksCount: Int = 0,
    val totalBricksThousand: Double = 0.0,
    val mortarCementBags: Double = 0.0,
    val finishingAreaM2: Double = 0.0,
    val excavationM3: Double = 0.0,
    val waterproofingAreaM2: Double = 0.0
)

data class SpecDocumentUiState(
    val sourceMode: SpecSourceMode = SpecSourceMode.DOCUMENT,
    val selectedFileKind: EngineeringFileReader.FileKind? = null,
    val selectedFileName: String = "",
    val selectedDocUri: Uri? = null,
    val extractedDocumentText: String = "",
    val pastedSpecText: String = "",
    val pdfPageCount: Int = 0,
    val pdfStartPage: Int = 1,
    val pdfPagesToAnalyze: Int = 4,
    val previewBitmaps: List<Bitmap> = emptyList(),
    val defaultHeightM: String = "3.0",
    val defaultSlabThicknessM: String = "0.15",
    val defaultMixGrade: ConcreteGrade = ConcreteGrade.C25,
    val defaultBrickType: MasonryEstimator.BrickType = MasonryEstimator.BrickType.SUDANESE_RED_BRICK,
    val defaultSteelDiameter: RebarDiameter = RebarDiameter.D16,
    val wastagePercent: String = "5.0",
    val steelRateKgPerM3: String = "110.0",
    val userContextNote: String = "",
    val analysisStatus: BlueprintAnalysisStatus = BlueprintAnalysisStatus.Idle,
    val report: SpecDocumentAnalysisReport? = null,
    val aggregatedTotals: SpecAggregatedTotals = SpecAggregatedTotals()
)

data class ConcreteUiState(
    val elementType: StructuralElementType = StructuralElementType.COLUMN_RECT,
    val lengthM: String = "4.0",
    val widthM: String = "0.25",
    val depthHeightM: String = "3.0",
    val count: String = "10",
    val slabThicknessM: String = "0.15",
    val circularDiameterM: String = "0.30",
    val stairsRiserCount: String = "10",
    val stairsRiserHeightM: String = "0.15",
    val stairsTreadLengthM: String = "0.30",
    val stairsWaistThicknessM: String = "0.15",
    val stairsLandingLengthM: String = "1.2",
    val stairsLandingWidthM: String = "2.4",
    val directVolumeM3: String = "10.0",
    val mixGrade: ConcreteGrade = ConcreteGrade.C25,
    val customCementBagsPerM3: String = "8.0",
    val customSandRatio: String = "0.42",
    val customGravelRatio: String = "0.84",
    val wastagePercent: String = "5.0",
    val steelRatioKgPerM3: String = "140.0",
    val rebarDiameter: RebarDiameter = RebarDiameter.D16,
    val result: ConcreteEstimateResult? = null
)

data class SteelUiState(
    val isDetailedMode: Boolean = true,
    val diameter: RebarDiameter = RebarDiameter.D16,
    val numberOfBars: String = "8",
    val barLengthM: String = "3.5",
    val lapLengthM: String = "0.8", // ~50d for D16
    val isIncludeStirrups: Boolean = true,
    val stirrupDiameter: RebarDiameter = RebarDiameter.D8,
    val memberLengthM: String = "3.0",
    val stirrupSpacingCm: String = "15.0",
    val memberWidthM: String = "0.25",
    val memberDepthM: String = "0.50",
    val concreteCoverCm: String = "2.5",
    val wastePercentage: String = "5.0",
    val detailedResult: SteelDetailedResult? = null,
    // Empirical mode
    val empiricalConcreteVolumeM3: String = "15.0",
    val empiricalSteelRatioKgPerM3: String = "120.0",
    val empiricalDiameter: RebarDiameter = RebarDiameter.D16,
    val empiricalTotalKg: Double = 0.0,
    val empiricalTotalTonnes: Double = 0.0,
    val empiricalPrimaryBreakdown: RebarDiameterBreakdown? = null,
    val empiricalDiameterOptions: List<RebarDiameterBreakdown> = emptyList()
)

data class MasonryUiState(
    val wallLengthM: String = "12.0",
    val wallHeightM: String = "3.0",
    val openingsAreaM2: String = "4.5", // أبواب وشبابيك
    val brickType: MasonryEstimator.BrickType = MasonryEstimator.BrickType.SUDANESE_RED_BRICK,
    val wallThickness: MasonryEstimator.WallThickness = MasonryEstimator.WallThickness.HALF_BRICK,
    val wastePercentage: String = "7.0",
    val result: MasonryResult? = null
)

enum class FinishingTab(val titleAr: String) {
    PLASTER("البياض والمحارة"),
    TILES("السيراميك والبورسلين"),
    PAINT("الدهانات والمعجون")
}

data class FinishingUiState(
    val currentTab: FinishingTab = FinishingTab.PLASTER,
    // Plaster
    val plasterAreaM2: String = "150.0",
    val plasterThicknessCm: String = "2.0",
    val plasterType: PlasterFinishingEstimator.PlasterType = PlasterFinishingEstimator.PlasterType.INTERNAL_PLASTER,
    val plasterWastePercent: String = "10.0",
    val plasterResult: PlasterResult? = null,
    // Tiles
    val tilesAreaM2: String = "80.0",
    val tileLengthCm: String = "60.0",
    val tileWidthCm: String = "60.0",
    val tilesPerBox: String = "4",
    val tilesWastePercent: String = "8.0",
    val tilesResult: TilesResult? = null,
    // Paint
    val paintAreaM2: String = "200.0",
    val paintCoatsCount: String = "2",
    val paintResult: PaintingResult? = null
)

data class MarketPricesSdg(
    val cementTonPrice: Double = 1450000.0,      // طن الأسمنت (20 شوال)
    val steelTonPrice: Double = 2100000.0,       // طن حديد التسليح
    val sandSmallTruckPrice: Double = 120000.0,  // قلاب رمل 4م3
    val sandTataTruckPrice: Double = 460000.0,   // قلاب رمل تاتة 16م3
    val gravelTataTruckPrice: Double = 580000.0, // قلاب كنكري تاتة 16م3
    val redBricksPerThousand: Double = 180000.0, // ألف طوبة بلدي
    val hollowBlockUnitPrice: Double = 950.0,    // حبة البلك 20 سم
    val waterDrumPrice: Double = 12000.0,        // برميل الماء 200 لتر
    val plasterPerM2Price: Double = 18000.0,     // متر البياض (مونة ومصنعية)
    val tilesPerM2Price: Double = 35000.0,       // متر السيراميك والمونة
    val paintPerM2Price: Double = 15000.0        // متر الدهان والمعجون
)

class CivilQuantitiesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ProjectRepository

    init {
        val db = AppDatabase.getDatabase(application)
        repository = ProjectRepository(db.projectDao(), db.workPlanDao())
    }

    val savedProjects = repository.allProjects

    val workPlans: StateFlow<List<WorkPlanWithPhases>> = repository.allWorkPlans
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedWorkPlanId = MutableStateFlow<Long?>(null)
    val selectedWorkPlanId: StateFlow<Long?> = _selectedWorkPlanId.asStateFlow()

    private val _currentScreen = MutableStateFlow(AppScreen.HOME)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val screenBackStack = ArrayDeque<AppScreen>()
    private val _isNavigatingForward = MutableStateFlow(true)
    val isNavigatingForward: StateFlow<Boolean> = _isNavigatingForward.asStateFlow()

    private val _concreteState = MutableStateFlow(ConcreteUiState())
    val concreteState: StateFlow<ConcreteUiState> = _concreteState.asStateFlow()

    private val _steelState = MutableStateFlow(SteelUiState())
    val steelState: StateFlow<SteelUiState> = _steelState.asStateFlow()

    private val _masonryState = MutableStateFlow(MasonryUiState())
    val masonryState: StateFlow<MasonryUiState> = _masonryState.asStateFlow()

    private val _finishingState = MutableStateFlow(FinishingUiState())
    val finishingState: StateFlow<FinishingUiState> = _finishingState.asStateFlow()

    private val _marketPrices = MutableStateFlow(MarketPricesSdg())
    val marketPrices: StateFlow<MarketPricesSdg> = _marketPrices.asStateFlow()

    private val _boqItems = MutableStateFlow<List<BoqItem>>(emptyList())
    val boqItems: StateFlow<List<BoqItem>> = _boqItems.asStateFlow()

    private val _blueprintState = MutableStateFlow(BlueprintAnalyzerUiState())
    val blueprintState: StateFlow<BlueprintAnalyzerUiState> = _blueprintState.asStateFlow()

    private val _specState = MutableStateFlow(SpecDocumentUiState())
    val specState: StateFlow<SpecDocumentUiState> = _specState.asStateFlow()

    private val _snackMessage = MutableSharedFlow<String>()
    val snackMessage: SharedFlow<String> = _snackMessage.asSharedFlow()

    init {
        // Initial calculations
        calculateConcrete()
        calculateSteel()
        calculateEmpiricalSteel()
        calculateMasonry()
        calculateFinishingPlaster()
        calculateFinishingTiles()
        calculateFinishingPaint()
        generateInitialBoq()
    }

    fun navigateTo(screen: AppScreen) {
        val current = _currentScreen.value
        if (current == screen) return
        if (screen == AppScreen.HOME) {
            screenBackStack.clear()
            _isNavigatingForward.value = false
        } else {
            if (current != AppScreen.HOME || screenBackStack.isEmpty()) {
                screenBackStack.addLast(current)
            }
            _isNavigatingForward.value = true
        }
        _currentScreen.value = screen
    }

    fun navigateBack() {
        _isNavigatingForward.value = false
        if (screenBackStack.isNotEmpty()) {
            _currentScreen.value = screenBackStack.removeLast()
        } else {
            _currentScreen.value = AppScreen.HOME
        }
    }

    // --- CONCRETE ACTIONS ---
    fun updateConcreteElementType(type: StructuralElementType) {
        val suggestedDiameter = when (type) {
            StructuralElementType.SOLID_SLAB,
            StructuralElementType.RIBBED_SLAB,
            StructuralElementType.SLAB_ON_GRADE -> RebarDiameter.D10
            StructuralElementType.FLAT_SLAB,
            StructuralElementType.STAIRS -> RebarDiameter.D12
            StructuralElementType.RAFT_FOUNDATION -> RebarDiameter.D20
            else -> RebarDiameter.D16
        }
        _concreteState.update {
            it.copy(
                elementType = type,
                steelRatioKgPerM3 = type.steelRatioKgPerM3.toString(),
                rebarDiameter = suggestedDiameter
            )
        }
        calculateConcrete()
    }

    fun updateConcreteDimensions(
        length: String? = null,
        width: String? = null,
        depthHeight: String? = null,
        count: String? = null,
        slabThickness: String? = null,
        circularDiameter: String? = null,
        directVolume: String? = null
    ) {
        _concreteState.update {
            it.copy(
                lengthM = length ?: it.lengthM,
                widthM = width ?: it.widthM,
                depthHeightM = depthHeight ?: it.depthHeightM,
                count = count ?: it.count,
                slabThicknessM = slabThickness ?: it.slabThicknessM,
                circularDiameterM = circularDiameter ?: it.circularDiameterM,
                directVolumeM3 = directVolume ?: it.directVolumeM3
            )
        }
        calculateConcrete()
    }

    fun updateConcreteMix(mixGrade: ConcreteGrade) {
        _concreteState.update { it.copy(mixGrade = mixGrade) }
        calculateConcrete()
    }

    fun updateConcreteCustomMix(cementBags: String, sand: String, gravel: String) {
        _concreteState.update {
            it.copy(
                customCementBagsPerM3 = cementBags,
                customSandRatio = sand,
                customGravelRatio = gravel
            )
        }
        calculateConcrete()
    }

    fun updateConcreteWastage(waste: String) {
        _concreteState.update { it.copy(wastagePercent = waste) }
        calculateConcrete()
    }

    fun updateConcreteSteelRatio(ratio: String) {
        _concreteState.update { it.copy(steelRatioKgPerM3 = ratio) }
        calculateConcrete()
    }

    fun updateConcreteRebarDiameter(diameter: RebarDiameter) {
        _concreteState.update { it.copy(rebarDiameter = diameter) }
        calculateConcrete()
    }

    fun calculateConcrete() {
        val s = _concreteState.value
        val l = s.lengthM.toDoubleOrNull() ?: 1.0
        val w = s.widthM.toDoubleOrNull() ?: 1.0
        val d = s.depthHeightM.toDoubleOrNull() ?: 1.0
        val cnt = s.count.toIntOrNull() ?: 1
        val slabThk = s.slabThicknessM.toDoubleOrNull() ?: 0.15
        val circDia = s.circularDiameterM.toDoubleOrNull() ?: 0.30
        val directVol = s.directVolumeM3.toDoubleOrNull() ?: 1.0
        val waste = s.wastagePercent.toDoubleOrNull() ?: 5.0
        val customCement = s.customCementBagsPerM3.toDoubleOrNull() ?: 8.0
        val customSand = s.customSandRatio.toDoubleOrNull() ?: 0.42
        val customGravel = s.customGravelRatio.toDoubleOrNull() ?: 0.84
        val steelRatio = s.steelRatioKgPerM3.toDoubleOrNull() ?: s.elementType.steelRatioKgPerM3

        val netVol = ConcreteEstimator.calculateNetVolume(
            elementType = s.elementType,
            lengthMeters = l,
            widthMeters = w,
            heightDepthMeters = d,
            count = cnt,
            slabThicknessMeters = slabThk,
            circularDiameterMeters = circDia,
            customDirectVolumeM3 = directVol
        )

        val result = ConcreteEstimator.calculateMaterials(
            netVolumeM3 = netVol,
            mixGrade = s.mixGrade,
            wastagePercent = waste,
            customCementBagsPerM3 = customCement,
            customSandRatio = customSand,
            customGravelRatio = customGravel,
            steelRatioKgPerM3 = steelRatio,
            rebarDiameter = s.rebarDiameter
        )

        _concreteState.update { it.copy(result = result) }
    }

    // --- STEEL ACTIONS ---
    fun toggleSteelMode(isDetailed: Boolean) {
        _steelState.update { it.copy(isDetailedMode = isDetailed) }
        if (isDetailed) calculateSteel() else calculateEmpiricalSteel()
    }

    fun updateSteelDetailed(
        diameter: RebarDiameter? = null,
        numberOfBars: String? = null,
        barLengthM: String? = null,
        lapLengthM: String? = null,
        isIncludeStirrups: Boolean? = null,
        stirrupDiameter: RebarDiameter? = null,
        memberLengthM: String? = null,
        stirrupSpacingCm: String? = null,
        memberWidthM: String? = null,
        memberDepthM: String? = null,
        wastePercentage: String? = null
    ) {
        _steelState.update {
            it.copy(
                diameter = diameter ?: it.diameter,
                numberOfBars = numberOfBars ?: it.numberOfBars,
                barLengthM = barLengthM ?: it.barLengthM,
                lapLengthM = lapLengthM ?: it.lapLengthM,
                isIncludeStirrups = isIncludeStirrups ?: it.isIncludeStirrups,
                stirrupDiameter = stirrupDiameter ?: it.stirrupDiameter,
                memberLengthM = memberLengthM ?: it.memberLengthM,
                stirrupSpacingCm = stirrupSpacingCm ?: it.stirrupSpacingCm,
                memberWidthM = memberWidthM ?: it.memberWidthM,
                memberDepthM = memberDepthM ?: it.memberDepthM,
                wastePercentage = wastePercentage ?: it.wastePercentage
            )
        }
        calculateSteel()
    }

    fun calculateSteel() {
        val s = _steelState.value
        val barsCount = s.numberOfBars.toIntOrNull() ?: 1
        val barLen = s.barLengthM.toDoubleOrNull() ?: 1.0
        val lapLen = s.lapLengthM.toDoubleOrNull() ?: 0.0
        val memLen = s.memberLengthM.toDoubleOrNull() ?: 3.0
        val spacing = s.stirrupSpacingCm.toDoubleOrNull() ?: 15.0
        val memW = s.memberWidthM.toDoubleOrNull() ?: 0.25
        val memD = s.memberDepthM.toDoubleOrNull() ?: 0.50
        val cover = s.concreteCoverCm.toDoubleOrNull() ?: 2.5
        val waste = s.wastePercentage.toDoubleOrNull() ?: 5.0

        val result = SteelEstimator.calculateDetailedRebar(
            diameter = s.diameter,
            numberOfBars = barsCount,
            barLengthMeters = barLen,
            lapLengthMeters = lapLen,
            isIncludeStirrups = s.isIncludeStirrups,
            stirrupDiameter = s.stirrupDiameter,
            memberLengthMeters = memLen,
            stirrupSpacingCm = spacing,
            memberWidthMeters = memW,
            memberDepthMeters = memD,
            concreteCoverCm = cover,
            wastePercentage = waste
        )

        _steelState.update { it.copy(detailedResult = result) }
    }

    fun updateEmpiricalSteel(volume: String, ratio: String) {
        _steelState.update { it.copy(empiricalConcreteVolumeM3 = volume, empiricalSteelRatioKgPerM3 = ratio) }
        calculateEmpiricalSteel()
    }

    fun updateEmpiricalSteelDiameter(diameter: RebarDiameter) {
        _steelState.update { it.copy(empiricalDiameter = diameter) }
        calculateEmpiricalSteel()
    }

    fun calculateEmpiricalSteel() {
        val s = _steelState.value
        val vol = s.empiricalConcreteVolumeM3.toDoubleOrNull() ?: 0.0
        val ratio = s.empiricalSteelRatioKgPerM3.toDoubleOrNull() ?: 120.0
        val (kg, tonnes) = SteelEstimator.calculateByEmpiricalRatio(vol, ratio)
        val (primary, options) = SteelEstimator.calculateEmpiricalDiameterBreakdown(kg, s.empiricalDiameter)
        _steelState.update {
            it.copy(
                empiricalTotalKg = kg,
                empiricalTotalTonnes = tonnes,
                empiricalPrimaryBreakdown = primary,
                empiricalDiameterOptions = options
            )
        }
    }

    // --- MASONRY ACTIONS ---
    fun updateMasonry(
        length: String? = null,
        height: String? = null,
        openings: String? = null,
        brickType: MasonryEstimator.BrickType? = null,
        wallThickness: MasonryEstimator.WallThickness? = null,
        waste: String? = null
    ) {
        _masonryState.update {
            it.copy(
                wallLengthM = length ?: it.wallLengthM,
                wallHeightM = height ?: it.wallHeightM,
                openingsAreaM2 = openings ?: it.openingsAreaM2,
                brickType = brickType ?: it.brickType,
                wallThickness = wallThickness ?: it.wallThickness,
                wastePercentage = waste ?: it.wastePercentage
            )
        }
        calculateMasonry()
    }

    fun calculateMasonry() {
        val s = _masonryState.value
        val l = s.wallLengthM.toDoubleOrNull() ?: 0.0
        val h = s.wallHeightM.toDoubleOrNull() ?: 0.0
        val op = s.openingsAreaM2.toDoubleOrNull() ?: 0.0
        val waste = s.wastePercentage.toDoubleOrNull() ?: 5.0

        val result = MasonryEstimator.calculateMasonry(
            wallLengthMeters = l,
            wallHeightMeters = h,
            openingsAreaM2 = op,
            brickType = s.brickType,
            wallThickness = s.wallThickness,
            wastagePercentage = waste
        )
        _masonryState.update { it.copy(result = result) }
    }

    // --- FINISHING ACTIONS ---
    fun setFinishingTab(tab: FinishingTab) {
        _finishingState.update { it.copy(currentTab = tab) }
    }

    fun updatePlaster(area: String? = null, thickness: String? = null, type: PlasterFinishingEstimator.PlasterType? = null) {
        _finishingState.update {
            it.copy(
                plasterAreaM2 = area ?: it.plasterAreaM2,
                plasterThicknessCm = thickness ?: it.plasterThicknessCm,
                plasterType = type ?: it.plasterType
            )
        }
        calculateFinishingPlaster()
    }

    fun calculateFinishingPlaster() {
        val s = _finishingState.value
        val a = s.plasterAreaM2.toDoubleOrNull() ?: 0.0
        val thk = s.plasterThicknessCm.toDoubleOrNull() ?: 2.0
        val waste = s.plasterWastePercent.toDoubleOrNull() ?: 10.0
        val res = PlasterFinishingEstimator.calculatePlaster(a, thk, s.plasterType, waste)
        _finishingState.update { it.copy(plasterResult = res) }
    }

    fun updateTiles(area: String? = null, length: String? = null, width: String? = null, box: String? = null) {
        _finishingState.update {
            it.copy(
                tilesAreaM2 = area ?: it.tilesAreaM2,
                tileLengthCm = length ?: it.tileLengthCm,
                tileWidthCm = width ?: it.tileWidthCm,
                tilesPerBox = box ?: it.tilesPerBox
            )
        }
        calculateFinishingTiles()
    }

    fun calculateFinishingTiles() {
        val s = _finishingState.value
        val a = s.tilesAreaM2.toDoubleOrNull() ?: 0.0
        val l = s.tileLengthCm.toDoubleOrNull() ?: 60.0
        val w = s.tileWidthCm.toDoubleOrNull() ?: 60.0
        val b = s.tilesPerBox.toIntOrNull() ?: 4
        val waste = s.tilesWastePercent.toDoubleOrNull() ?: 8.0
        val res = PlasterFinishingEstimator.calculateTiles(a, l, w, b, waste)
        _finishingState.update { it.copy(tilesResult = res) }
    }

    fun updatePaint(area: String? = null, coats: String? = null) {
        _finishingState.update {
            it.copy(
                paintAreaM2 = area ?: it.paintAreaM2,
                paintCoatsCount = coats ?: it.paintCoatsCount
            )
        }
        calculateFinishingPaint()
    }

    fun calculateFinishingPaint() {
        val s = _finishingState.value
        val a = s.paintAreaM2.toDoubleOrNull() ?: 0.0
        val c = s.paintCoatsCount.toIntOrNull() ?: 2
        val res = PlasterFinishingEstimator.calculatePainting(a, c)
        _finishingState.update { it.copy(paintResult = res) }
    }

    // --- MARKET PRICES & BOQ ---
    fun updateMarketPrices(prices: MarketPricesSdg) {
        _marketPrices.value = prices
        recalculateBoqWithPrices()
    }

    fun updatePrice(key: String, price: Double) {
        val current = _marketPrices.value
        val updated = when (key) {
            "cement" -> current.copy(cementTonPrice = price)
            "steel" -> current.copy(steelTonPrice = price)
            "sandSmall" -> current.copy(sandSmallTruckPrice = price)
            "sandTata" -> current.copy(sandTataTruckPrice = price)
            "gravelTata" -> current.copy(gravelTataTruckPrice = price)
            "redBricks" -> current.copy(redBricksPerThousand = price)
            "hollowBlock" -> current.copy(hollowBlockUnitPrice = price)
            "waterDrum" -> current.copy(waterDrumPrice = price)
            "plasterM2" -> current.copy(plasterPerM2Price = price)
            "tileM2" -> current.copy(tilesPerM2Price = price)
            "paintM2" -> current.copy(paintPerM2Price = price)
            else -> current
        }
        _marketPrices.value = updated
        recalculateBoqWithPrices()
    }

    fun generateInitialBoq() {
        val prices = _marketPrices.value
        val concreteRes = _concreteState.value.result
        val steelRes = _steelState.value.detailedResult
        val masonryRes = _masonryState.value.result
        val plasterRes = _finishingState.value.plasterResult
        val tilesRes = _finishingState.value.tilesResult

        val items = mutableListOf<BoqItem>()

        if (concreteRes != null) {
            items.add(
                BoqItem(
                    id = "c_cement",
                    categoryAr = "الخرسانة",
                    itemDescriptionAr = "أسمنت بورتلاندي عادي للخرسانة (${concreteRes.cementBags} شوال)",
                    quantity = concreteRes.cementTonnes,
                    unitAr = "طن",
                    unitPriceSdg = prices.cementTonPrice
                )
            )
            items.add(
                BoqItem(
                    id = "c_sand",
                    categoryAr = "الخرسانة",
                    itemDescriptionAr = "رمل أحمر/نظيف خرسانة (${concreteRes.sandM3} م³)",
                    quantity = concreteRes.sandLargeTataTrucks.coerceAtLeast(0.5),
                    unitAr = "لوري تاتة (16م³)",
                    unitPriceSdg = prices.sandTataTruckPrice
                )
            )
            items.add(
                BoqItem(
                    id = "c_gravel",
                    categoryAr = "الخرسانة",
                    itemDescriptionAr = "كنكري / سن مكسر مقاس 3/4 (${concreteRes.gravelM3} م³)",
                    quantity = concreteRes.gravelLargeTataTrucks.coerceAtLeast(0.5),
                    unitAr = "لوري تاتة (16م³)",
                    unitPriceSdg = prices.gravelTataTruckPrice
                )
            )
            items.add(
                BoqItem(
                    id = "c_steel",
                    categoryAr = "الخرسانة",
                    itemDescriptionAr = "حديد تسليح تقديري قطر ${concreteRes.rebarDiameter.mm} مم (${concreteRes.estimatedSteelKg} كجم | ${concreteRes.estimatedSteel12mBarsRounded} سيخة 12م)",
                    quantity = concreteRes.estimatedSteelTonnes,
                    unitAr = "طن",
                    unitPriceSdg = prices.steelTonPrice
                )
            )
            items.add(
                BoqItem(
                    id = "c_water",
                    categoryAr = "الخرسانة",
                    itemDescriptionAr = "مياه خلط ورش صبية",
                    quantity = concreteRes.waterDrums,
                    unitAr = "برميل",
                    unitPriceSdg = prices.waterDrumPrice
                )
            )
        }

        if (steelRes != null) {
            steelRes.diameterBreakdown.forEachIndexed { idx, row ->
                items.add(
                    BoqItem(
                        id = if (idx == 0) "s_main" else "s_sub_${row.diameter.mm}",
                        categoryAr = "حديد التسليح",
                        itemDescriptionAr = "${row.roleLabelAr.ifBlank { "تسليح" }} قطر ${row.diameter.mm} مم — وزن ${row.weightKg} كجم (${row.bars12mRounded} سيخة 12م)",
                        quantity = row.weightTonnes,
                        unitAr = "طن",
                        unitPriceSdg = prices.steelTonPrice
                    )
                )
            }
        }

        if (masonryRes != null) {
            items.add(
                BoqItem(
                    id = "m_bricks",
                    categoryAr = "المباني",
                    itemDescriptionAr = "طوب أحمر محروق بلدي (${masonryRes.totalBricksCount} طوبة)",
                    quantity = masonryRes.totalBricksThousand,
                    unitAr = "ألف طوبة",
                    unitPriceSdg = prices.redBricksPerThousand
                )
            )
            items.add(
                BoqItem(
                    id = "m_mortar_cement",
                    categoryAr = "المباني",
                    itemDescriptionAr = "أسمنت لمونة المباني (${masonryRes.cementBagsForMortar} شوال)",
                    quantity = masonryRes.cementBagsForMortar / 20.0,
                    unitAr = "طن",
                    unitPriceSdg = prices.cementTonPrice
                )
            )
            items.add(
                BoqItem(
                    id = "m_mortar_sand",
                    categoryAr = "المباني",
                    itemDescriptionAr = "رمل مباني (${masonryRes.sandM3ForMortar} م³)",
                    quantity = masonryRes.sandSmallTrucks.coerceAtLeast(1.0),
                    unitAr = "قلاب صغير (4م³)",
                    unitPriceSdg = prices.sandSmallTruckPrice
                )
            )
        }

        if (plasterRes != null) {
            items.add(
                BoqItem(
                    id = "fin_plaster",
                    categoryAr = "التشطيبات",
                    itemDescriptionAr = "بياض ومحارة داخلية شامل المونة والتشوين (${plasterRes.areaM2} م²)",
                    quantity = plasterRes.areaM2,
                    unitAr = "م²",
                    unitPriceSdg = prices.plasterPerM2Price
                )
            )
        }

        if (tilesRes != null) {
            items.add(
                BoqItem(
                    id = "fin_tiles",
                    categoryAr = "التشطيبات",
                    itemDescriptionAr = "سيراميك أرضيات وبورسلين شامل المونة والتركيب (${tilesRes.floorAreaM2} م²)",
                    quantity = tilesRes.floorAreaM2,
                    unitAr = "م²",
                    unitPriceSdg = prices.tilesPerM2Price
                )
            )
        }

        _boqItems.value = items
    }

    private fun recalculateBoqWithPrices() {
        val prices = _marketPrices.value
        val updated = _boqItems.value.map { item ->
            val updatedPrice = when (item.id) {
                "c_cement", "m_mortar_cement" -> prices.cementTonPrice
                "c_sand" -> prices.sandTataTruckPrice
                "c_gravel" -> prices.gravelTataTruckPrice
                "c_steel", "s_main" -> prices.steelTonPrice
                "c_water" -> prices.waterDrumPrice
                "m_bricks" -> prices.redBricksPerThousand
                "m_mortar_sand" -> prices.sandSmallTruckPrice
                "fin_plaster" -> prices.plasterPerM2Price
                "fin_tiles" -> prices.tilesPerM2Price
                else -> if (item.id.startsWith("s_sub_") || item.id.startsWith("bp_steel_") || item.id.startsWith("spec_steel_")) {
                    prices.steelTonPrice
                } else {
                    item.unitPriceSdg
                }
            }
            item.copy(unitPriceSdg = updatedPrice)
        }
        _boqItems.value = updated
    }

    fun syncCalculatorsToBoq(showToast: Boolean = true) {
        val preservedExtras = _boqItems.value.filter { item ->
            val isDefaultCalcId = item.id.startsWith("c_") ||
                item.id == "s_main" ||
                item.id.startsWith("s_sub_") ||
                item.id.startsWith("m_") ||
                item.id.startsWith("fin_")
            !isDefaultCalcId
        }
        generateInitialBoq()
        if (preservedExtras.isNotEmpty()) {
            _boqItems.update { preservedExtras + it }
        }
        if (showToast) {
            viewModelScope.launch {
                _snackMessage.emit("تم تحديث جدول الكميات بأحدث الحسابات")
            }
        }
    }

    fun addCustomBoqItem(category: String, desc: String, qty: Double, unit: String, price: Double) {
        val newItem = BoqItem(
            id = UUID.randomUUID().toString(),
            categoryAr = category,
            itemDescriptionAr = desc,
            quantity = qty,
            unitAr = unit,
            unitPriceSdg = price
        )
        _boqItems.update { it + newItem }
    }

    fun updateBoqItem(id: String, desc: String, qty: Double, unitPrice: Double) {
        _boqItems.update { list ->
            list.map { item ->
                if (item.id == id) {
                    item.copy(
                        itemDescriptionAr = desc.ifBlank { item.itemDescriptionAr },
                        quantity = qty.coerceAtLeast(0.0),
                        unitPriceSdg = unitPrice.coerceAtLeast(0.0)
                    )
                } else item
            }
        }
    }

    fun adjustBoqItemQuantity(id: String, delta: Double) {
        _boqItems.update { list ->
            list.map { item ->
                if (item.id == id) {
                    val nextQty = ((item.quantity + delta).coerceAtLeast(0.1) * 100.0).roundToInt() / 100.0
                    item.copy(quantity = nextQty)
                } else item
            }
        }
    }

    fun deleteBoqItem(id: String) {
        _boqItems.update { list -> list.filterNot { it.id == id } }
    }

    // --- SAVE / DELETE / EXPORT PROJECTS ---
    fun saveCurrentCalculation(
        title: String,
        location: String,
        category: String,
        onSaved: () -> Unit
    ) {
        viewModelScope.launch {
            val totalCost = _boqItems.value.sumOf { it.totalPriceSdg }
            val summary = when (category) {
                "خرسانة" -> {
                    val r = _concreteState.value.result
                    "خرسانة ${r?.volumeWithWasteM3 ?: 0.0} م³ | أسمنت: ${r?.cementBags ?: 0.0} شوال | رمل: ${r?.sandM3 ?: 0.0} م³ | حديد Ø${r?.rebarDiameter?.mm ?: 16}: ${r?.estimatedSteelKg ?: 0.0} كجم (${r?.estimatedSteel12mBarsRounded ?: 0} سيخة 12م)"
                }
                "تسليح" -> {
                    val sState = _steelState.value
                    if (sState.isDetailedMode) {
                        val r = sState.detailedResult
                        val parts = r?.diameterBreakdown?.joinToString(" + ") { row ->
                            "Ø${row.diameter.mm}: ${row.weightKg} كجم (${row.bars12mRounded} سيخة 12م)"
                        } ?: "حديد قطر ${r?.diameter?.mm ?: 16} مم: ${r?.totalWeightKg ?: 0.0} كجم"
                        "$parts | الإجمالي: ${r?.totalWeightTonnes ?: 0.0} طن"
                    } else {
                        val p = sState.empiricalPrimaryBreakdown
                        "تقدير حديد Ø${sState.empiricalDiameter.mm}: ${sState.empiricalTotalKg} كجم (${sState.empiricalTotalTonnes} طن) — ${p?.bars12mRounded ?: 0} سيخة 12م"
                    }
                }
                "مباني" -> {
                    val r = _masonryState.value.result
                    "طوب: ${r?.totalBricksCount ?: 0} طوبة (${r?.totalBricksThousand ?: 0.0} ألف) | أسمنت مونة: ${r?.cementBagsForMortar ?: 0.0} شوال"
                }
                "تشطيبات" -> {
                    val p = _finishingState.value.plasterResult
                    val t = _finishingState.value.tilesResult
                    "بياض: ${p?.areaM2 ?: 0.0} م² | سيراميك: ${t?.floorAreaM2 ?: 0.0} م²"
                }
                else -> {
                    "مقايسة مشروع شاملة (${_boqItems.value.size} بنود) | إجمالي: ${formatSdg(totalCost)} جنيه سوداني"
                }
            }

            val detailsBuilder = StringBuilder()
            detailsBuilder.append("مشروع: $title\nالموقع: $location\nالتاريخ: ${System.currentTimeMillis()}\n\n")
            detailsBuilder.append("--- بنود المقايسة والكميات ---\n")
            _boqItems.value.forEach { item ->
                detailsBuilder.append("• ${item.itemDescriptionAr}: ${item.quantity} ${item.unitAr} × ${formatSdg(item.unitPriceSdg)} = ${formatSdg(item.totalPriceSdg)} SDG\n")
            }
            detailsBuilder.append("\nالإجمالي الكلي: ${formatSdg(totalCost)} جنيه سوداني\n")

            val entity = ProjectEntity(
                title = title.ifBlank { "مشروع كميات جديد" },
                clientOrLocation = location.ifBlank { "الخرطوم، السودان" },
                category = category,
                summaryText = summary,
                detailedDetails = detailsBuilder.toString(),
                totalCostSdg = totalCost
            )

            repository.saveProject(entity)
            _snackMessage.emit("تم حفظ المشروع بنجاح!")
            onSaved()
        }
    }

    fun deleteProject(project: ProjectEntity) {
        viewModelScope.launch {
            repository.deleteProject(project)
            _snackMessage.emit("تم حذف المشروع")
        }
    }

    // --- BLUEPRINT, PDF, CAD DXF & ENGINEERING SCHEDULE ANALYZER ---

    fun onBlueprintImagesSelected(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val app = getApplication<Application>()
        viewModelScope.launch {
            _blueprintState.update {
                it.copy(
                    analysisStatus = BlueprintAnalysisStatus.Loading("جاري تجهيز صور المخطط..."),
                    selectedFileKind = EngineeringFileReader.FileKind.IMAGE,
                    selectedImageUris = uris,
                    selectedDocUri = null,
                    dxfParseResult = null,
                    scheduleTextContent = null,
                    pdfPageCount = 0,
                    report = null
                )
            }
            try {
                val bitmaps = withContext(Dispatchers.IO) {
                    uris.take(4).map { uri -> EngineeringFileReader.loadScaledBitmap(app, uri) }
                }
                val names = uris.take(4).map { resolveDisplayName(app, it) }
                _blueprintState.update {
                    it.copy(
                        selectedFileNames = names,
                        previewBitmaps = bitmaps,
                        analysisStatus = BlueprintAnalysisStatus.Idle
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _blueprintState.update {
                    it.copy(analysisStatus = BlueprintAnalysisStatus.Error("تعذر تحميل الصور: ${e.localizedMessage}"))
                }
            }
        }
    }

    fun onEngineeringDocumentSelected(uri: Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val fileName = resolveDisplayName(app, uri)
            val mime = app.contentResolver.getType(uri)
            val kind = EngineeringFileReader.detectFileKind(fileName, mime)

            _blueprintState.update {
                it.copy(
                    selectedFileKind = kind,
                    selectedFileNames = listOf(fileName),
                    selectedDocUri = uri,
                    selectedImageUris = emptyList(),
                    previewBitmaps = emptyList(),
                    dxfParseResult = null,
                    scheduleTextContent = null,
                    pdfPageCount = 0,
                    currentPdfPageIndex = 0,
                    report = null,
                    analysisStatus = BlueprintAnalysisStatus.Loading("جاري قراءة الملف الهندسي ($fileName)...")
                )
            }

            try {
                when (kind) {
                    EngineeringFileReader.FileKind.IMAGE -> {
                        val bmp = withContext(Dispatchers.IO) {
                            EngineeringFileReader.loadScaledBitmap(app, uri)
                        }
                        _blueprintState.update {
                            it.copy(
                                previewBitmaps = listOf(bmp),
                                analysisStatus = BlueprintAnalysisStatus.Idle
                            )
                        }
                    }
                    EngineeringFileReader.FileKind.PDF -> {
                        val (count, firstPageBmp) = withContext(Dispatchers.IO) {
                            val total = EngineeringFileReader.getPdfPageCount(app, uri)
                            val bmp = if (total > 0) EngineeringFileReader.renderPdfPageToBitmap(app, uri, 0) else null
                            total to bmp
                        }
                        _blueprintState.update {
                            it.copy(
                                pdfPageCount = count,
                                currentPdfPageIndex = 0,
                                previewBitmaps = listOfNotNull(firstPageBmp),
                                analysisStatus = if (count > 0) BlueprintAnalysisStatus.Idle
                                else BlueprintAnalysisStatus.Error("ملف PDF لا يحتوي على صفحات صالحة.")
                            )
                        }
                    }
                    EngineeringFileReader.FileKind.DXF -> {
                        val parsed = withContext(Dispatchers.IO) {
                            val rawText = EngineeringFileReader.readTextFromUri(app, uri)
                            DxfQuantityParser.parseDxf(rawText)
                        }
                        val s = _blueprintState.value
                        val h = s.defaultHeightM.toDoubleOrNull() ?: 3.0
                        val slabThk = s.defaultSlabThicknessM.toDoubleOrNull() ?: 0.15
                        val report = BlueprintAiAnalyzer.convertDxfToElementsOffline(
                            dxfResult = parsed,
                            unitOverride = parsed.detectedUnit,
                            defaultHeightM = h,
                            defaultSlabThicknessM = slabThk
                        )
                        _blueprintState.update {
                            it.copy(
                                dxfParseResult = parsed,
                                dxfUnit = parsed.detectedUnit,
                                report = report,
                                analysisStatus = BlueprintAnalysisStatus.Success
                            )
                        }
                        recalculateBlueprintTotals()
                    }
                    EngineeringFileReader.FileKind.DOCX -> {
                        val textContent = withContext(Dispatchers.IO) {
                            EngineeringFileReader.readDocxFromUri(app, uri)
                        }
                        _blueprintState.update {
                            it.copy(
                                scheduleTextContent = textContent,
                                analysisStatus = BlueprintAnalysisStatus.Idle
                            )
                        }
                    }
                    EngineeringFileReader.FileKind.XLSX -> {
                        val textContent = withContext(Dispatchers.IO) {
                            EngineeringFileReader.readXlsxFromUri(app, uri)
                        }
                        _blueprintState.update {
                            it.copy(
                                scheduleTextContent = textContent,
                                analysisStatus = BlueprintAnalysisStatus.Idle
                            )
                        }
                    }
                    EngineeringFileReader.FileKind.SCHEDULE_TEXT -> {
                        val textContent = withContext(Dispatchers.IO) {
                            EngineeringFileReader.readTextFromUri(app, uri)
                        }
                        _blueprintState.update {
                            it.copy(
                                scheduleTextContent = textContent,
                                analysisStatus = BlueprintAnalysisStatus.Idle
                            )
                        }
                    }
                    EngineeringFileReader.FileKind.UNSUPPORTED -> {
                        _blueprintState.update {
                            it.copy(
                                analysisStatus = BlueprintAnalysisStatus.Error(
                                    "صيغة الملف غير مدعومة مباشرة. يرجى تصدير الملف بصيغة DXF أو PDF أو Word (.docx) أو Excel (.xlsx) أو CSV أو صورة."
                                )
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _blueprintState.update {
                    it.copy(
                        analysisStatus = BlueprintAnalysisStatus.Error("تعذر قراءة الملف: ${e.localizedMessage ?: "تأكد من صلاحية الملف"}")
                    )
                }
            }
        }
    }

    fun selectPdfPage(newIndex: Int) {
        val s = _blueprintState.value
        val uri = s.selectedDocUri ?: return
        if (s.selectedFileKind != EngineeringFileReader.FileKind.PDF) return
        val safeIdx = newIndex.coerceIn(0, (s.pdfPageCount - 1).coerceAtLeast(0))
        val app = getApplication<Application>()
        viewModelScope.launch {
            _blueprintState.update {
                it.copy(analysisStatus = BlueprintAnalysisStatus.Loading("جاري عرض الصفحة ${safeIdx + 1}..."))
            }
            try {
                val bmp = withContext(Dispatchers.IO) {
                    EngineeringFileReader.renderPdfPageToBitmap(app, uri, safeIdx)
                }
                _blueprintState.update {
                    it.copy(
                        currentPdfPageIndex = safeIdx,
                        previewBitmaps = listOf(bmp),
                        analysisStatus = BlueprintAnalysisStatus.Idle
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _blueprintState.update {
                    it.copy(analysisStatus = BlueprintAnalysisStatus.Error("تعذر عرض صفحة PDF: ${e.localizedMessage}"))
                }
            }
        }
    }

    fun updateDxfUnit(unit: CadDrawingUnit) {
        val s = _blueprintState.value
        val dxf = s.dxfParseResult ?: return
        val h = s.defaultHeightM.toDoubleOrNull() ?: 3.0
        val slabThk = s.defaultSlabThicknessM.toDoubleOrNull() ?: 0.15
        val report = BlueprintAiAnalyzer.convertDxfToElementsOffline(
            dxfResult = dxf,
            unitOverride = unit,
            defaultHeightM = h,
            defaultSlabThicknessM = slabThk
        )
        _blueprintState.update {
            it.copy(
                dxfUnit = unit,
                report = report,
                analysisStatus = BlueprintAnalysisStatus.Success
            )
        }
        recalculateBlueprintTotals()
    }

    fun updateBlueprintDefaults(
        heightM: String? = null,
        slabThicknessM: String? = null,
        mixGrade: ConcreteGrade? = null,
        brickType: MasonryEstimator.BrickType? = null,
        steelDiameter: RebarDiameter? = null,
        wastagePercent: String? = null,
        steelRateKgPerM3: String? = null,
        userContextNote: String? = null
    ) {
        _blueprintState.update {
            it.copy(
                defaultHeightM = heightM ?: it.defaultHeightM,
                defaultSlabThicknessM = slabThicknessM ?: it.defaultSlabThicknessM,
                defaultMixGrade = mixGrade ?: it.defaultMixGrade,
                defaultBrickType = brickType ?: it.defaultBrickType,
                defaultSteelDiameter = steelDiameter ?: it.defaultSteelDiameter,
                wastagePercent = wastagePercent ?: it.wastagePercent,
                steelRateKgPerM3 = steelRateKgPerM3 ?: it.steelRateKgPerM3,
                userContextNote = userContextNote ?: it.userContextNote
            )
        }
        recalculateBlueprintTotals()
    }

    fun runBlueprintAiAnalysis() {
        val s = _blueprintState.value
        val h = s.defaultHeightM.toDoubleOrNull() ?: 3.0
        val slabThk = s.defaultSlabThicknessM.toDoubleOrNull() ?: 0.15

        viewModelScope.launch {
            _blueprintState.update {
                it.copy(analysisStatus = BlueprintAnalysisStatus.Loading("جاري تحليل المخطط وحساب الكميات بالذكاء الاصطناعي..."))
            }
            try {
                val report = when (s.selectedFileKind) {
                    EngineeringFileReader.FileKind.IMAGE,
                    EngineeringFileReader.FileKind.PDF -> {
                        if (s.previewBitmaps.isEmpty()) {
                            throw IllegalStateException("الرجاء اختيار صورة مخطط أو ملف PDF أولاً.")
                        }
                        BlueprintAiAnalyzer.analyzeBlueprintImages(
                            bitmaps = s.previewBitmaps,
                            userContextNote = s.userContextNote,
                            defaultHeightM = h,
                            defaultSlabThicknessM = slabThk
                        )
                    }
                    EngineeringFileReader.FileKind.DXF -> {
                        val dxf = s.dxfParseResult
                            ?: throw IllegalStateException("لا توجد بيانات DXF محملة.")
                        val summaryText = buildString {
                            appendLine("وحدة الرسم: ${s.dxfUnit.arName} (معامل التحويل للمتر: ${s.dxfUnit.toMetersFactor})")
                            dxf.layers.forEach { l ->
                                val lenM = (l.totalLengthInDrawingUnits * s.dxfUnit.toMetersFactor)
                                val areaM2 = (l.totalClosedAreaInDrawingUnitsSq * s.dxfUnit.toMetersFactor * s.dxfUnit.toMetersFactor)
                                appendLine("- طبقة [${l.layerName}]: خطوط=${l.lineCount}, مضلعات=${l.polylineCount} (مغلق=${l.closedPolylineCount}), دوائر=${l.circleCount}, بلوكات=${l.blockInsertCount} (${l.blockNames}), طول=${String.format(Locale.US, "%.2f", lenM)} م, مساحة=${String.format(Locale.US, "%.2f", areaM2)} م², نصوص=${l.textsInLayer.take(10)}")
                            }
                            if (dxf.allAnnotations.isNotEmpty()) {
                                appendLine("نصوص عامة في اللوحة: ${dxf.allAnnotations.take(50)}")
                            }
                        }
                        BlueprintAiAnalyzer.analyzeEngineeringTextData(
                            fileName = s.selectedFileNames.firstOrNull() ?: "drawing.dxf",
                            extractedSummaryText = summaryText,
                            defaultHeightM = h,
                            defaultSlabThicknessM = slabThk,
                            userContextNote = s.userContextNote
                        )
                    }
                    EngineeringFileReader.FileKind.DOCX,
                    EngineeringFileReader.FileKind.XLSX,
                    EngineeringFileReader.FileKind.SCHEDULE_TEXT -> {
                        val text = s.scheduleTextContent
                            ?: throw IllegalStateException("لا توجد بيانات جدول محملة.")
                        BlueprintAiAnalyzer.analyzeEngineeringTextData(
                            fileName = s.selectedFileNames.firstOrNull() ?: "schedule.csv",
                            extractedSummaryText = text,
                            defaultHeightM = h,
                            defaultSlabThicknessM = slabThk,
                            userContextNote = s.userContextNote
                        )
                    }
                    else -> throw IllegalStateException("يرجى اختيار مخطط أو ملف هندسي أولاً.")
                }

                _blueprintState.update {
                    it.copy(
                        report = report,
                        defaultMixGrade = report.recommendedMixGrade,
                        analysisStatus = BlueprintAnalysisStatus.Success
                    )
                }
                recalculateBlueprintTotals()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _blueprintState.update {
                    it.copy(
                        analysisStatus = BlueprintAnalysisStatus.Error(
                            e.localizedMessage ?: "حدث خطأ أثناء تحليل المخطط."
                        )
                    )
                }
            }
        }
    }

    fun toggleBlueprintElement(elementId: String) {
        val currentReport = _blueprintState.value.report ?: return
        val updatedList = currentReport.elements.map { el ->
            if (el.id == elementId) el.copy(isIncluded = !el.isIncluded) else el
        }
        _blueprintState.update { it.copy(report = currentReport.copy(elements = updatedList)) }
        recalculateBlueprintTotals()
    }

    fun updateBlueprintElementValues(
        elementId: String,
        count: Int,
        lengthM: Double,
        widthM: Double,
        heightOrThicknessM: Double
    ) {
        val currentReport = _blueprintState.value.report ?: return
        val updatedList = currentReport.elements.map { el ->
            if (el.id != elementId) {
                el
            } else {
                val safeCount = count.coerceAtLeast(1)
                val newVol = when (el.category) {
                    ExtractedElementCategory.CONCRETE ->
                        ((safeCount * lengthM * widthM * heightOrThicknessM) * 100.0).roundToInt() / 100.0
                    else -> el.volumeM3
                }
                val newArea = when (el.category) {
                    ExtractedElementCategory.MASONRY,
                    ExtractedElementCategory.FINISHING ->
                        ((safeCount * lengthM * (if (heightOrThicknessM > 0) heightOrThicknessM else widthM)) * 100.0).roundToInt() / 100.0
                    else -> el.areaM2
                }
                val newWeight = if (el.category == ExtractedElementCategory.STEEL && el.diameterMm > 0) {
                    ((safeCount * lengthM * RebarDiameter.fromMm(el.diameterMm).weightPerMeterKg) * 10.0).roundToInt() / 10.0
                } else {
                    el.weightKg
                }
                el.copy(
                    count = safeCount,
                    lengthM = lengthM,
                    widthM = widthM,
                    heightOrThicknessM = heightOrThicknessM,
                    volumeM3 = newVol,
                    areaM2 = newArea,
                    weightKg = newWeight
                )
            }
        }
        _blueprintState.update { it.copy(report = currentReport.copy(elements = updatedList)) }
        recalculateBlueprintTotals()
    }

    fun deleteBlueprintElement(elementId: String) {
        val currentReport = _blueprintState.value.report ?: return
        val updatedList = currentReport.elements.filterNot { it.id == elementId }
        _blueprintState.update { it.copy(report = currentReport.copy(elements = updatedList)) }
        recalculateBlueprintTotals()
    }

    private fun recalculateBlueprintTotals() {
        val s = _blueprintState.value
        val activeElements = s.report?.elements?.filter { it.isIncluded } ?: emptyList()
        if (activeElements.isEmpty()) {
            _blueprintState.update { it.copy(aggregatedTotals = BlueprintAggregatedTotals()) }
            return
        }

        val waste = s.wastagePercent.toDoubleOrNull() ?: 5.0
        val steelRate = s.steelRateKgPerM3.toDoubleOrNull() ?: 110.0

        val concreteNetM3 = activeElements
            .filter { it.category == ExtractedElementCategory.CONCRETE }
            .sumOf { it.volumeM3 }

        val concreteRes = ConcreteEstimator.calculateMaterials(
            netVolumeM3 = concreteNetM3,
            mixGrade = s.defaultMixGrade,
            wastagePercent = waste,
            steelRatioKgPerM3 = steelRate,
            rebarDiameter = s.defaultSteelDiameter
        )

        val explicitSteelElements = activeElements.filter { it.category == ExtractedElementCategory.STEEL }
        val explicitSteelKg = explicitSteelElements.sumOf { it.weightKg }

        val totalSteelKg = if (explicitSteelKg > 0.0) {
            explicitSteelKg * (1.0 + waste / 100.0)
        } else {
            concreteRes.estimatedSteelKg
        }
        val totalSteelTonnes = totalSteelKg / 1000.0
        val equivD16Bars = totalSteelKg / s.defaultSteelDiameter.weightPer12mBarKg

        val steelByDiameter: List<RebarDiameterBreakdown> = if (explicitSteelElements.isNotEmpty()) {
            explicitSteelElements
                .groupBy { el ->
                    if (el.diameterMm > 0) RebarDiameter.fromMm(el.diameterMm) else s.defaultSteelDiameter
                }
                .toSortedMap(compareBy { it.mm })
                .map { (diameter, list) ->
                    val netKg = list.sumOf { it.weightKg }
                    val grossKg = netKg * (1.0 + waste / 100.0)
                    val cutCount = list.sumOf { it.count }
                    SteelEstimator.breakdownFromWeightKg(
                        weightKg = grossKg,
                        diameter = diameter,
                        cutBarsCount = if (cutCount > 0) cutCount else null,
                        roleLabelAr = "مستخرج من المخطط"
                    )
                }
        } else if (totalSteelKg > 0.0) {
            listOf(
                SteelEstimator.breakdownFromWeightKg(
                    weightKg = totalSteelKg,
                    diameter = s.defaultSteelDiameter,
                    roleLabelAr = "تقديري من حجم الخرسانة"
                )
            )
        } else {
            emptyList()
        }

        val masonryAreaM2 = activeElements
            .filter { it.category == ExtractedElementCategory.MASONRY }
            .sumOf { it.areaM2 }

        val masonryRes = if (masonryAreaM2 > 0.0) {
            MasonryEstimator.calculateMasonry(
                wallLengthMeters = masonryAreaM2,
                wallHeightMeters = 1.0,
                brickType = s.defaultBrickType,
                wastagePercentage = waste
            )
        } else null

        val finishingAreaM2 = activeElements
            .filter { it.category == ExtractedElementCategory.FINISHING }
            .sumOf { it.areaM2 }

        _blueprintState.update {
            it.copy(
                aggregatedTotals = BlueprintAggregatedTotals(
                    totalConcreteNetM3 = (concreteNetM3 * 100.0).roundToInt() / 100.0,
                    totalConcreteGrossM3 = concreteRes.volumeWithWasteM3,
                    cementBags50Kg = concreteRes.cementBags,
                    cementTonnes = concreteRes.cementTonnes,
                    sandM3 = concreteRes.sandM3,
                    sandTataTrucks = concreteRes.sandLargeTataTrucks,
                    gravelM3 = concreteRes.gravelM3,
                    gravelTataTrucks = concreteRes.gravelLargeTataTrucks,
                    waterDrums = concreteRes.waterDrums,
                    explicitSteelKg = (explicitSteelKg * 10.0).roundToInt() / 10.0,
                    estimatedTotalSteelKg = (totalSteelKg * 10.0).roundToInt() / 10.0,
                    estimatedTotalSteelTonnes = (totalSteelTonnes * 1000.0).roundToInt() / 1000.0,
                    equivalent12mBarsD16 = (equivD16Bars * 10.0).roundToInt() / 10.0,
                    steelByDiameter = steelByDiameter,
                    masonryAreaM2 = (masonryAreaM2 * 100.0).roundToInt() / 100.0,
                    totalBricksCount = masonryRes?.totalBricksCount ?: 0,
                    totalBricksThousand = masonryRes?.totalBricksThousand ?: 0.0,
                    mortarCementBags = masonryRes?.cementBagsForMortar ?: 0.0,
                    mortarSandM3 = masonryRes?.sandM3ForMortar ?: 0.0,
                    finishingAreaM2 = (finishingAreaM2 * 100.0).roundToInt() / 100.0
                )
            )
        }
    }

    fun pushBlueprintTotalsToBoq() {
        val s = _blueprintState.value
        val totals = s.aggregatedTotals
        val prices = _marketPrices.value
        val title = s.report?.drawingTitle ?: "المخطط"

        val newItems = mutableListOf<BoqItem>()
        if (totals.totalConcreteGrossM3 > 0.0) {
            newItems.add(
                BoqItem(
                    id = "bp_cement_${UUID.randomUUID()}",
                    categoryAr = "الخرسانة (من المخطط)",
                    itemDescriptionAr = "أسمنت خرسانة [$title] (${totals.cementBags50Kg} شوال - ${s.defaultMixGrade.code})",
                    quantity = totals.cementTonnes,
                    unitAr = "طن",
                    unitPriceSdg = prices.cementTonPrice
                )
            )
            newItems.add(
                BoqItem(
                    id = "bp_sand_${UUID.randomUUID()}",
                    categoryAr = "الخرسانة (من المخطط)",
                    itemDescriptionAr = "رمل خرساني [$title] (${totals.sandM3} م³)",
                    quantity = totals.sandTataTrucks.coerceAtLeast(0.5),
                    unitAr = "لوري تاتة (16م³)",
                    unitPriceSdg = prices.sandTataTruckPrice
                )
            )
            newItems.add(
                BoqItem(
                    id = "bp_gravel_${UUID.randomUUID()}",
                    categoryAr = "الخرسانة (من المخطط)",
                    itemDescriptionAr = "كنكري خرسانة [$title] (${totals.gravelM3} م³)",
                    quantity = totals.gravelTataTrucks.coerceAtLeast(0.5),
                    unitAr = "لوري تاتة (16م³)",
                    unitPriceSdg = prices.gravelTataTruckPrice
                )
            )
            newItems.add(
                BoqItem(
                    id = "bp_water_${UUID.randomUUID()}",
                    categoryAr = "الخرسانة (من المخطط)",
                    itemDescriptionAr = "مياه صب ومعالجة [$title]",
                    quantity = totals.waterDrums,
                    unitAr = "برميل",
                    unitPriceSdg = prices.waterDrumPrice
                )
            )
        }
        if (totals.steelByDiameter.isNotEmpty()) {
            totals.steelByDiameter.forEach { dRow ->
                newItems.add(
                    BoqItem(
                        id = "bp_steel_${dRow.diameter.mm}_${UUID.randomUUID()}",
                        categoryAr = "حديد التسليح (من المخطط)",
                        itemDescriptionAr = "حديد تسليح قطر ${dRow.diameter.mm} مم [$title] — ${dRow.weightKg} كجم (${dRow.bars12mRounded} سيخة 12م)",
                        quantity = dRow.weightTonnes,
                        unitAr = "طن",
                        unitPriceSdg = prices.steelTonPrice
                    )
                )
            }
        } else if (totals.estimatedTotalSteelTonnes > 0.0) {
            newItems.add(
                BoqItem(
                    id = "bp_steel_${UUID.randomUUID()}",
                    categoryAr = "حديد التسليح (من المخطط)",
                    itemDescriptionAr = "حديد تسليح [$title] (~${ceil(totals.equivalent12mBarsD16).toInt()} سيخة 12م)",
                    quantity = totals.estimatedTotalSteelTonnes,
                    unitAr = "طن",
                    unitPriceSdg = prices.steelTonPrice
                )
            )
        }
        if (totals.totalBricksThousand > 0.0) {
            newItems.add(
                BoqItem(
                    id = "bp_bricks_${UUID.randomUUID()}",
                    categoryAr = "المباني (من المخطط)",
                    itemDescriptionAr = "${s.defaultBrickType.arName} [$title] (${totals.totalBricksCount} طوبة)",
                    quantity = totals.totalBricksThousand,
                    unitAr = "ألف طوبة",
                    unitPriceSdg = prices.redBricksPerThousand
                )
            )
            if (totals.mortarCementBags > 0.0) {
                newItems.add(
                    BoqItem(
                        id = "bp_mortar_cement_${UUID.randomUUID()}",
                        categoryAr = "المباني (من المخطط)",
                        itemDescriptionAr = "أسمنت مونة مباني [$title] (${totals.mortarCementBags} شوال)",
                        quantity = ((totals.mortarCementBags / 20.0) * 100.0).roundToInt() / 100.0,
                        unitAr = "طن",
                        unitPriceSdg = prices.cementTonPrice
                    )
                )
            }
        }
        if (totals.finishingAreaM2 > 0.0) {
            newItems.add(
                BoqItem(
                    id = "bp_finish_${UUID.randomUUID()}",
                    categoryAr = "التشطيبات (من المخطط)",
                    itemDescriptionAr = "أعمال تشطيبات وبياض مستخرجة من [$title]",
                    quantity = totals.finishingAreaM2,
                    unitAr = "م²",
                    unitPriceSdg = prices.plasterPerM2Price
                )
            )
        }

        if (newItems.isNotEmpty()) {
            _boqItems.update { newItems + it }
            viewModelScope.launch {
                _snackMessage.emit("تم ترحيل ${newItems.size} بند من المخطط إلى جدول الكميات (BOQ)")
            }
        }
    }

    fun saveBlueprintProject(title: String, location: String, onSaved: () -> Unit) {
        val s = _blueprintState.value
        val totals = s.aggregatedTotals
        val report = s.report
        viewModelScope.launch {
            val steelBreakdownSummary = totals.steelByDiameter.joinToString(" | ") {
                "Ø${it.diameter.mm}: ${it.weightKg} كجم (${it.bars12mRounded} سيخة)"
            }
            val summary = "خرسانة: ${totals.totalConcreteGrossM3} م³ | أسمنت: ${totals.cementBags50Kg} شوال | حديد: ${totals.estimatedTotalSteelTonnes} طن${if (steelBreakdownSummary.isNotBlank()) " ($steelBreakdownSummary)" else ""} | طوب: ${totals.totalBricksThousand} ألف"
            val details = buildString {
                appendLine("المصدر: ${s.selectedFileNames.joinToString(", ")}")
                appendLine("المخطط: ${report?.drawingTitle ?: title}")
                appendLine("الرتبة الخرسانية: ${s.defaultMixGrade.code}")
                appendLine("--- الملخص السوقي بالسودان ---")
                appendLine("• الخرسانة الكلية: ${totals.totalConcreteGrossM3} م³")
                appendLine("• الأسمنت: ${totals.cementBags50Kg} شوال (${totals.cementTonnes} طن)")
                appendLine("• الرمل: ${totals.sandM3} م³ (${totals.sandTataTrucks} لوري تاتة)")
                appendLine("• الكنكري: ${totals.gravelM3} م³ (${totals.gravelTataTrucks} لوري تاتة)")
                appendLine("• الحديد الإجمالي: ${totals.estimatedTotalSteelTonnes} طن (${totals.estimatedTotalSteelKg} كجم)")
                totals.steelByDiameter.forEach { dRow ->
                    appendLine("  - قطر ${dRow.diameter.mm} مم: ${dRow.weightKg} كجم (${dRow.weightTonnes} طن) | ${dRow.bars12mRounded} سيخة 12م")
                }
                appendLine("• المباني: ${totals.totalBricksCount} طوبة (${totals.totalBricksThousand} ألف)")
                appendLine("\n--- العناصر المستخرجة ---")
                report?.elements?.filter { it.isIncluded }?.forEach { el ->
                    appendLine("- ${el.nameAr} (${el.category.arLabel}): عدد ${el.count} | ${el.lengthM}×${el.widthM}×${el.heightOrThicknessM}م | حجم ${el.volumeM3}م³ | مساحة ${el.areaM2}م²")
                }
            }
            val prices = _marketPrices.value
            val estCost = (totals.cementTonnes * prices.cementTonPrice) +
                (totals.sandTataTrucks * prices.sandTataTruckPrice) +
                (totals.gravelTataTrucks * prices.gravelTataTruckPrice) +
                (totals.estimatedTotalSteelTonnes * prices.steelTonPrice) +
                (totals.totalBricksThousand * prices.redBricksPerThousand)

            repository.saveProject(
                ProjectEntity(
                    title = title.ifBlank { report?.drawingTitle ?: "مشروع مخطط هندسي" },
                    clientOrLocation = location.ifBlank { "السودان" },
                    category = "مخطط هندسي",
                    summaryText = summary,
                    detailedDetails = details,
                    totalCostSdg = estCost
                )
            )
            _snackMessage.emit("تم حفظ كميات المخطط في المشاريع المحفوظة!")
            onSaved()
        }
    }

    // --- SPECIFICATION BOOKLETS & DOCUMENTS ANALYZER ---

    fun setSpecSourceMode(mode: SpecSourceMode) {
        _specState.update { it.copy(sourceMode = mode, analysisStatus = BlueprintAnalysisStatus.Idle) }
    }

    fun updateSpecPastedText(text: String) {
        _specState.update { it.copy(pastedSpecText = text) }
    }

    fun onSpecImagesSelected(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val app = getApplication<Application>()
        viewModelScope.launch {
            _specState.update {
                it.copy(
                    sourceMode = SpecSourceMode.IMAGES,
                    selectedFileKind = EngineeringFileReader.FileKind.IMAGE,
                    selectedDocUri = null,
                    extractedDocumentText = "",
                    pdfPageCount = 0,
                    report = null,
                    analysisStatus = BlueprintAnalysisStatus.Loading("جاري تجهيز صفحات كراسة المواصفات...")
                )
            }
            try {
                val limited = uris.take(6)
                val bitmaps = withContext(Dispatchers.IO) {
                    limited.map { EngineeringFileReader.loadScaledBitmap(app, it, maxDimensionPx = 1400) }
                }
                val names = limited.map { resolveDisplayName(app, it) }
                _specState.update {
                    it.copy(
                        selectedFileName = if (names.size == 1) names.first() else "${names.size} صفحات كراسة مواصفات",
                        previewBitmaps = bitmaps,
                        analysisStatus = BlueprintAnalysisStatus.Idle
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _specState.update {
                    it.copy(analysisStatus = BlueprintAnalysisStatus.Error("تعذر تحميل الصور: ${e.localizedMessage}"))
                }
            }
        }
    }

    fun onSpecDocumentSelected(uri: Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val fileName = resolveDisplayName(app, uri)
            val mime = app.contentResolver.getType(uri)
            val kind = EngineeringFileReader.detectFileKind(fileName, mime)

            _specState.update {
                it.copy(
                    sourceMode = SpecSourceMode.DOCUMENT,
                    selectedFileKind = kind,
                    selectedFileName = fileName,
                    selectedDocUri = uri,
                    extractedDocumentText = "",
                    previewBitmaps = emptyList(),
                    pdfPageCount = 0,
                    pdfStartPage = 1,
                    pdfPagesToAnalyze = 4,
                    report = null,
                    analysisStatus = BlueprintAnalysisStatus.Loading("جاري قراءة المستند ($fileName)...")
                )
            }

            try {
                when (kind) {
                    EngineeringFileReader.FileKind.DOCX -> {
                        val text = withContext(Dispatchers.IO) {
                            EngineeringFileReader.readDocxFromUri(app, uri)
                        }
                        _specState.update {
                            it.copy(
                                extractedDocumentText = text,
                                analysisStatus = BlueprintAnalysisStatus.Idle
                            )
                        }
                    }
                    EngineeringFileReader.FileKind.XLSX -> {
                        val text = withContext(Dispatchers.IO) {
                            EngineeringFileReader.readXlsxFromUri(app, uri)
                        }
                        _specState.update {
                            it.copy(
                                extractedDocumentText = text,
                                analysisStatus = BlueprintAnalysisStatus.Idle
                            )
                        }
                    }
                    EngineeringFileReader.FileKind.SCHEDULE_TEXT -> {
                        val text = withContext(Dispatchers.IO) {
                            EngineeringFileReader.readTextFromUri(app, uri)
                        }
                        _specState.update {
                            it.copy(
                                extractedDocumentText = text,
                                analysisStatus = BlueprintAnalysisStatus.Idle
                            )
                        }
                    }
                    EngineeringFileReader.FileKind.PDF -> {
                        val (total, bmps) = withContext(Dispatchers.IO) {
                            val totalPages = EngineeringFileReader.getPdfPageCount(app, uri)
                            val pages = if (totalPages > 0) {
                                EngineeringFileReader.renderPdfPagesRange(
                                    context = app,
                                    uri = uri,
                                    startPageIndexZeroBased = 0,
                                    maxPages = minOf(4, totalPages)
                                )
                            } else emptyList()
                            totalPages to pages
                        }
                        _specState.update {
                            it.copy(
                                pdfPageCount = total,
                                pdfStartPage = 1,
                                pdfPagesToAnalyze = minOf(4, total).coerceAtLeast(1),
                                previewBitmaps = bmps,
                                analysisStatus = if (total > 0) BlueprintAnalysisStatus.Idle
                                else BlueprintAnalysisStatus.Error("ملف PDF لا يحتوي على صفحات صالحة.")
                            )
                        }
                    }
                    EngineeringFileReader.FileKind.IMAGE -> {
                        val bmp = withContext(Dispatchers.IO) {
                            EngineeringFileReader.loadScaledBitmap(app, uri, maxDimensionPx = 1400)
                        }
                        _specState.update {
                            it.copy(
                                previewBitmaps = listOf(bmp),
                                analysisStatus = BlueprintAnalysisStatus.Idle
                            )
                        }
                    }
                    EngineeringFileReader.FileKind.DXF -> {
                        val dxfText = withContext(Dispatchers.IO) {
                            EngineeringFileReader.readTextFromUri(app, uri)
                        }
                        val parsed = DxfQuantityParser.parseDxf(dxfText)
                        val summary = buildString {
                            appendLine("ملف DXF: $fileName (${parsed.totalEntities} عنصر)")
                            parsed.allAnnotations.forEach { appendLine(it) }
                        }
                        _specState.update {
                            it.copy(
                                extractedDocumentText = summary,
                                analysisStatus = BlueprintAnalysisStatus.Idle
                            )
                        }
                    }
                    EngineeringFileReader.FileKind.UNSUPPORTED -> {
                        _specState.update {
                            it.copy(
                                analysisStatus = BlueprintAnalysisStatus.Error(
                                    "صيغة غير مدعومة. الصيغ المدعومة: Word (.docx)، Excel (.xlsx)، PDF، CSV/TXT، والصور."
                                )
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _specState.update {
                    it.copy(
                        analysisStatus = BlueprintAnalysisStatus.Error(
                            "تعذر قراءة المستند: ${e.localizedMessage ?: "تأكد من صلاحية الملف"}"
                        )
                    )
                }
            }
        }
    }

    fun updateSpecPdfRange(startPage1Based: Int, pagesCount: Int) {
        val s = _specState.value
        val uri = s.selectedDocUri ?: return
        if (s.selectedFileKind != EngineeringFileReader.FileKind.PDF || s.pdfPageCount <= 0) return

        val safeStart = startPage1Based.coerceIn(1, s.pdfPageCount)
        val safeCount = pagesCount.coerceIn(1, minOf(6, s.pdfPageCount - safeStart + 1))
        val app = getApplication<Application>()

        viewModelScope.launch {
            _specState.update {
                it.copy(analysisStatus = BlueprintAnalysisStatus.Loading("جاري تجهيز الصفحات $safeStart - ${safeStart + safeCount - 1}..."))
            }
            try {
                val bmps = withContext(Dispatchers.IO) {
                    EngineeringFileReader.renderPdfPagesRange(
                        context = app,
                        uri = uri,
                        startPageIndexZeroBased = safeStart - 1,
                        maxPages = safeCount
                    )
                }
                _specState.update {
                    it.copy(
                        pdfStartPage = safeStart,
                        pdfPagesToAnalyze = safeCount,
                        previewBitmaps = bmps,
                        analysisStatus = BlueprintAnalysisStatus.Idle
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _specState.update {
                    it.copy(analysisStatus = BlueprintAnalysisStatus.Error("تعذر عرض صفحات PDF: ${e.localizedMessage}"))
                }
            }
        }
    }

    fun updateSpecDefaults(
        heightM: String? = null,
        slabThicknessM: String? = null,
        mixGrade: ConcreteGrade? = null,
        brickType: MasonryEstimator.BrickType? = null,
        steelDiameter: RebarDiameter? = null,
        wastagePercent: String? = null,
        steelRateKgPerM3: String? = null,
        userContextNote: String? = null
    ) {
        _specState.update {
            it.copy(
                defaultHeightM = heightM ?: it.defaultHeightM,
                defaultSlabThicknessM = slabThicknessM ?: it.defaultSlabThicknessM,
                defaultMixGrade = mixGrade ?: it.defaultMixGrade,
                defaultBrickType = brickType ?: it.defaultBrickType,
                defaultSteelDiameter = steelDiameter ?: it.defaultSteelDiameter,
                wastagePercent = wastagePercent ?: it.wastagePercent,
                steelRateKgPerM3 = steelRateKgPerM3 ?: it.steelRateKgPerM3,
                userContextNote = userContextNote ?: it.userContextNote
            )
        }
        recalculateSpecTotals()
    }

    fun runSpecDocumentAnalysis() {
        val s = _specState.value
        val h = s.defaultHeightM.toDoubleOrNull() ?: 3.0
        val slabThk = s.defaultSlabThicknessM.toDoubleOrNull() ?: 0.15
        val steelRate = s.steelRateKgPerM3.toDoubleOrNull() ?: 110.0

        viewModelScope.launch {
            _specState.update {
                it.copy(analysisStatus = BlueprintAnalysisStatus.Loading("جاري قراءة كراسة المواصفات واستخراج البنود والكميات..."))
            }
            try {
                val report = when (s.sourceMode) {
                    SpecSourceMode.PASTED_TEXT -> {
                        val txt = s.pastedSpecText
                        SpecDocumentAiAnalyzer.analyzeSpecificationText(
                            documentName = "نص كراسة مواصفات مباشر",
                            specText = txt,
                            defaultHeightM = h,
                            defaultSlabThicknessM = slabThk,
                            defaultSteelRateKgPerM3 = steelRate,
                            userContextNote = s.userContextNote
                        )
                    }
                    SpecSourceMode.IMAGES -> {
                        if (s.previewBitmaps.isEmpty()) {
                            throw IllegalStateException("الرجاء اختيار صور صفحات الكراسة أولاً.")
                        }
                        SpecDocumentAiAnalyzer.analyzeSpecificationImages(
                            documentName = s.selectedFileName.ifBlank { "صفحات كراسة مواصفات" },
                            pageBitmaps = s.previewBitmaps,
                            defaultHeightM = h,
                            defaultSlabThicknessM = slabThk,
                            defaultSteelRateKgPerM3 = steelRate,
                            userContextNote = s.userContextNote
                        )
                    }
                    SpecSourceMode.DOCUMENT -> {
                        when (s.selectedFileKind) {
                            EngineeringFileReader.FileKind.DOCX,
                            EngineeringFileReader.FileKind.XLSX,
                            EngineeringFileReader.FileKind.SCHEDULE_TEXT,
                            EngineeringFileReader.FileKind.DXF -> {
                                SpecDocumentAiAnalyzer.analyzeSpecificationText(
                                    documentName = s.selectedFileName,
                                    specText = s.extractedDocumentText,
                                    defaultHeightM = h,
                                    defaultSlabThicknessM = slabThk,
                                    defaultSteelRateKgPerM3 = steelRate,
                                    userContextNote = s.userContextNote
                                )
                            }
                            EngineeringFileReader.FileKind.PDF,
                            EngineeringFileReader.FileKind.IMAGE -> {
                                if (s.previewBitmaps.isEmpty()) {
                                    throw IllegalStateException("لم يتم تحميل صفحات المستند بعد.")
                                }
                                SpecDocumentAiAnalyzer.analyzeSpecificationImages(
                                    documentName = s.selectedFileName,
                                    pageBitmaps = s.previewBitmaps,
                                    defaultHeightM = h,
                                    defaultSlabThicknessM = slabThk,
                                    defaultSteelRateKgPerM3 = steelRate,
                                    userContextNote = s.userContextNote
                                )
                            }
                            else -> throw IllegalStateException("الرجاء اختيار ملف كراسة مواصفات أو التبديل إلى تبويب لصق النص.")
                        }
                    }
                }

                _specState.update {
                    it.copy(
                        report = report,
                        defaultMixGrade = report.recommendedMixGrade,
                        defaultSteelDiameter = report.defaultRebarDiameter,
                        analysisStatus = BlueprintAnalysisStatus.Success
                    )
                }
                recalculateSpecTotals()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _specState.update {
                    it.copy(
                        analysisStatus = BlueprintAnalysisStatus.Error(
                            e.localizedMessage ?: "حدث خطأ أثناء تحليل كراسة المواصفات."
                        )
                    )
                }
            }
        }
    }

    fun toggleSpecItem(itemId: String) {
        val current = _specState.value.report ?: return
        val updated = current.items.map { if (it.id == itemId) it.copy(isIncluded = !it.isIncluded) else it }
        _specState.update { it.copy(report = current.copy(items = updated)) }
        recalculateSpecTotals()
    }

    fun updateSpecItemValues(
        itemId: String,
        count: Int,
        lengthM: Double,
        widthM: Double,
        heightOrThicknessM: Double,
        explicitQuantity: Double,
        diameterMm: Int
    ) {
        val current = _specState.value.report ?: return
        val updated = current.items.map { item ->
            if (item.id != itemId) {
                item
            } else {
                val safeCount = count.coerceAtLeast(1)
                val safeQty = explicitQuantity.coerceAtLeast(0.0)
                val safeDia = if (diameterMm in listOf(8, 10, 12, 16, 20, 25, 32)) diameterMm else item.diameterMm
                var newVol = item.volumeM3
                var newArea = item.areaM2
                var newWeight = item.weightKg

                when (item.category) {
                    SpecItemCategory.CONCRETE, SpecItemCategory.EXCAVATION -> {
                        val geomVol = ((safeCount * lengthM * widthM * heightOrThicknessM) * 100.0).roundToInt() / 100.0
                        newVol = if (geomVol > 0.0) geomVol else safeQty
                    }
                    SpecItemCategory.MASONRY, SpecItemCategory.FINISHING, SpecItemCategory.WATERPROOFING -> {
                        val secondDim = if (heightOrThicknessM > 0.0) heightOrThicknessM else widthM
                        val geomArea = ((safeCount * lengthM * secondDim) * 100.0).roundToInt() / 100.0
                        newArea = if (geomArea > 0.0) geomArea else safeQty
                    }
                    SpecItemCategory.STEEL -> {
                        val effectiveDia = if (safeDia > 0) safeDia else _specState.value.defaultSteelDiameter.mm
                        val geomWeight = ((safeCount * lengthM * RebarDiameter.fromMm(effectiveDia).weightPerMeterKg) * 10.0).roundToInt() / 10.0
                        newWeight = if (geomWeight > 0.0) geomWeight else {
                            if (item.unitAr.contains("طن")) ((safeQty * 1000.0) * 10.0).roundToInt() / 10.0 else safeQty
                        }
                    }
                }

                item.copy(
                    count = safeCount,
                    lengthM = lengthM,
                    widthM = widthM,
                    heightOrThicknessM = heightOrThicknessM,
                    diameterMm = safeDia,
                    explicitQuantityFromDoc = safeQty,
                    volumeM3 = newVol,
                    areaM2 = newArea,
                    weightKg = newWeight
                )
            }
        }
        _specState.update { it.copy(report = current.copy(items = updated)) }
        recalculateSpecTotals()
    }

    fun deleteSpecItem(itemId: String) {
        val current = _specState.value.report ?: return
        val updated = current.items.filterNot { it.id == itemId }
        _specState.update { it.copy(report = current.copy(items = updated)) }
        recalculateSpecTotals()
    }

    private fun recalculateSpecTotals() {
        val s = _specState.value
        val activeItems = s.report?.items?.filter { it.isIncluded } ?: emptyList()
        if (activeItems.isEmpty()) {
            _specState.update { it.copy(aggregatedTotals = SpecAggregatedTotals()) }
            return
        }

        val waste = s.wastagePercent.toDoubleOrNull() ?: 5.0
        val steelRate = s.steelRateKgPerM3.toDoubleOrNull() ?: 110.0

        val excavationM3 = activeItems
            .filter { it.category == SpecItemCategory.EXCAVATION }
            .sumOf { if (it.volumeM3 > 0.0) it.volumeM3 else it.explicitQuantityFromDoc }

        val concreteNetM3 = activeItems
            .filter { it.category == SpecItemCategory.CONCRETE }
            .sumOf { if (it.volumeM3 > 0.0) it.volumeM3 else it.explicitQuantityFromDoc }

        val concreteRes = ConcreteEstimator.calculateMaterials(
            netVolumeM3 = concreteNetM3,
            mixGrade = s.defaultMixGrade,
            wastagePercent = waste,
            steelRatioKgPerM3 = steelRate,
            rebarDiameter = s.defaultSteelDiameter
        )

        val explicitSteelItems = activeItems.filter { it.category == SpecItemCategory.STEEL }
        val explicitSteelKg = explicitSteelItems.sumOf { it.weightKg }

        val totalSteelKg = if (explicitSteelKg > 0.0) {
            explicitSteelKg * (1.0 + waste / 100.0)
        } else {
            concreteRes.estimatedSteelKg
        }
        val totalSteelTonnes = totalSteelKg / 1000.0

        val steelByDiameter: List<RebarDiameterBreakdown> = if (explicitSteelItems.isNotEmpty()) {
            explicitSteelItems
                .groupBy { item ->
                    if (item.diameterMm in listOf(8, 10, 12, 16, 20, 25, 32)) {
                        RebarDiameter.fromMm(item.diameterMm)
                    } else {
                        s.defaultSteelDiameter
                    }
                }
                .toSortedMap(compareBy { it.mm })
                .map { (diameter, list) ->
                    val netKg = list.sumOf { it.weightKg }
                    val grossKg = netKg * (1.0 + waste / 100.0)
                    val cutCount = list.sumOf { it.count }
                    SteelEstimator.breakdownFromWeightKg(
                        weightKg = grossKg,
                        diameter = diameter,
                        cutBarsCount = if (cutCount > 0) cutCount else null,
                        roleLabelAr = "حسب بنود كراسة المواصفات"
                    )
                }
        } else if (totalSteelKg > 0.0) {
            listOf(
                SteelEstimator.breakdownFromWeightKg(
                    weightKg = totalSteelKg,
                    diameter = s.defaultSteelDiameter,
                    roleLabelAr = "تقديري من خرسانة الكراسة"
                )
            )
        } else {
            emptyList()
        }

        val masonryAreaM2 = activeItems
            .filter { it.category == SpecItemCategory.MASONRY }
            .sumOf { if (it.areaM2 > 0.0) it.areaM2 else it.explicitQuantityFromDoc }

        val masonryRes = if (masonryAreaM2 > 0.0) {
            MasonryEstimator.calculateMasonry(
                wallLengthMeters = masonryAreaM2,
                wallHeightMeters = 1.0,
                brickType = s.defaultBrickType,
                wastagePercentage = waste
            )
        } else null

        val finishingAreaM2 = activeItems
            .filter { it.category == SpecItemCategory.FINISHING }
            .sumOf { if (it.areaM2 > 0.0) it.areaM2 else it.explicitQuantityFromDoc }

        val waterproofingAreaM2 = activeItems
            .filter { it.category == SpecItemCategory.WATERPROOFING }
            .sumOf { if (it.areaM2 > 0.0) it.areaM2 else it.explicitQuantityFromDoc }

        _specState.update {
            it.copy(
                aggregatedTotals = SpecAggregatedTotals(
                    totalConcreteNetM3 = (concreteNetM3 * 100.0).roundToInt() / 100.0,
                    totalConcreteGrossM3 = concreteRes.volumeWithWasteM3,
                    cementBags50Kg = concreteRes.cementBags,
                    cementTonnes = concreteRes.cementTonnes,
                    sandM3 = concreteRes.sandM3,
                    sandTataTrucks = concreteRes.sandLargeTataTrucks,
                    gravelM3 = concreteRes.gravelM3,
                    gravelTataTrucks = concreteRes.gravelLargeTataTrucks,
                    waterDrums = concreteRes.waterDrums,
                    explicitSteelKg = (explicitSteelKg * 10.0).roundToInt() / 10.0,
                    estimatedTotalSteelKg = (totalSteelKg * 10.0).roundToInt() / 10.0,
                    estimatedTotalSteelTonnes = (totalSteelTonnes * 1000.0).roundToInt() / 1000.0,
                    steelByDiameter = steelByDiameter,
                    masonryAreaM2 = (masonryAreaM2 * 100.0).roundToInt() / 100.0,
                    totalBricksCount = masonryRes?.totalBricksCount ?: 0,
                    totalBricksThousand = masonryRes?.totalBricksThousand ?: 0.0,
                    mortarCementBags = masonryRes?.cementBagsForMortar ?: 0.0,
                    finishingAreaM2 = (finishingAreaM2 * 100.0).roundToInt() / 100.0,
                    excavationM3 = (excavationM3 * 100.0).roundToInt() / 100.0,
                    waterproofingAreaM2 = (waterproofingAreaM2 * 100.0).roundToInt() / 100.0
                )
            )
        }
    }

    fun pushSpecTotalsToBoq() {
        val s = _specState.value
        val totals = s.aggregatedTotals
        val prices = _marketPrices.value
        val title = s.report?.technicalSummary?.projectTitle ?: "كراسة المواصفات"

        val newItems = mutableListOf<BoqItem>()
        if (totals.excavationM3 > 0.0) {
            newItems.add(
                BoqItem(
                    id = "spec_exc_${UUID.randomUUID()}",
                    categoryAr = "الحفر والتأسيس (من الكراسة)",
                    itemDescriptionAr = "أعمال حفر وردم وتأسيس [$title] (${totals.excavationM3} م³)",
                    quantity = totals.excavationM3,
                    unitAr = "م³",
                    unitPriceSdg = 15000.0
                )
            )
        }
        if (totals.totalConcreteGrossM3 > 0.0) {
            newItems.add(
                BoqItem(
                    id = "spec_cement_${UUID.randomUUID()}",
                    categoryAr = "الخرسانة (من الكراسة)",
                    itemDescriptionAr = "أسمنت خرسانة [$title] (${totals.cementBags50Kg} شوال - ${s.defaultMixGrade.code})",
                    quantity = totals.cementTonnes,
                    unitAr = "طن",
                    unitPriceSdg = prices.cementTonPrice
                )
            )
            newItems.add(
                BoqItem(
                    id = "spec_sand_${UUID.randomUUID()}",
                    categoryAr = "الخرسانة (من الكراسة)",
                    itemDescriptionAr = "رمل خرساني نظيف [$title] (${totals.sandM3} م³)",
                    quantity = totals.sandTataTrucks.coerceAtLeast(0.5),
                    unitAr = "لوري تاتة (16م³)",
                    unitPriceSdg = prices.sandTataTruckPrice
                )
            )
            newItems.add(
                BoqItem(
                    id = "spec_gravel_${UUID.randomUUID()}",
                    categoryAr = "الخرسانة (من الكراسة)",
                    itemDescriptionAr = "كنكري خرسانة [$title] (${totals.gravelM3} م³)",
                    quantity = totals.gravelTataTrucks.coerceAtLeast(0.5),
                    unitAr = "لوري تاتة (16م³)",
                    unitPriceSdg = prices.gravelTataTruckPrice
                )
            )
            newItems.add(
                BoqItem(
                    id = "spec_water_${UUID.randomUUID()}",
                    categoryAr = "الخرسانة (من الكراسة)",
                    itemDescriptionAr = "مياه خلط ومعالجة [$title]",
                    quantity = totals.waterDrums,
                    unitAr = "برميل",
                    unitPriceSdg = prices.waterDrumPrice
                )
            )
        }
        if (totals.steelByDiameter.isNotEmpty()) {
            totals.steelByDiameter.forEach { dRow ->
                newItems.add(
                    BoqItem(
                        id = "spec_steel_${dRow.diameter.mm}_${UUID.randomUUID()}",
                        categoryAr = "حديد التسليح (من الكراسة)",
                        itemDescriptionAr = "حديد تسليح قطر ${dRow.diameter.mm} مم [$title] — ${dRow.weightKg} كجم (${dRow.bars12mRounded} سيخة 12م)",
                        quantity = dRow.weightTonnes,
                        unitAr = "طن",
                        unitPriceSdg = prices.steelTonPrice
                    )
                )
            }
        }
        if (totals.totalBricksThousand > 0.0) {
            newItems.add(
                BoqItem(
                    id = "spec_bricks_${UUID.randomUUID()}",
                    categoryAr = "المباني (من الكراسة)",
                    itemDescriptionAr = "${s.defaultBrickType.arName} [$title] (${totals.totalBricksCount} طوبة)",
                    quantity = totals.totalBricksThousand,
                    unitAr = "ألف طوبة",
                    unitPriceSdg = prices.redBricksPerThousand
                )
            )
            if (totals.mortarCementBags > 0.0) {
                newItems.add(
                    BoqItem(
                        id = "spec_mortar_cement_${UUID.randomUUID()}",
                        categoryAr = "المباني (من الكراسة)",
                        itemDescriptionAr = "أسمنت مونة مباني [$title] (${totals.mortarCementBags} شوال)",
                        quantity = ((totals.mortarCementBags / 20.0) * 100.0).roundToInt() / 100.0,
                        unitAr = "طن",
                        unitPriceSdg = prices.cementTonPrice
                    )
                )
            }
        }
        if (totals.finishingAreaM2 > 0.0) {
            newItems.add(
                BoqItem(
                    id = "spec_finish_${UUID.randomUUID()}",
                    categoryAr = "التشطيبات (من الكراسة)",
                    itemDescriptionAr = "أعمال بياض وتشطيبات من كراسة [$title]",
                    quantity = totals.finishingAreaM2,
                    unitAr = "م²",
                    unitPriceSdg = prices.plasterPerM2Price
                )
            )
        }
        if (totals.waterproofingAreaM2 > 0.0) {
            newItems.add(
                BoqItem(
                    id = "spec_wp_${UUID.randomUUID()}",
                    categoryAr = "العزل (من الكراسة)",
                    itemDescriptionAr = "عزل مائي ورطوبة [$title]",
                    quantity = totals.waterproofingAreaM2,
                    unitAr = "م²",
                    unitPriceSdg = 22000.0
                )
            )
        }

        if (newItems.isNotEmpty()) {
            _boqItems.update { newItems + it }
            viewModelScope.launch {
                _snackMessage.emit("تم ترحيل ${newItems.size} بند من كراسة المواصفات إلى جدول الكميات (BOQ)")
            }
        }
    }

    fun saveSpecProject(title: String, location: String, onSaved: () -> Unit) {
        val s = _specState.value
        val totals = s.aggregatedTotals
        val report = s.report ?: return
        viewModelScope.launch {
            val steelBreakdownSummary = totals.steelByDiameter.joinToString(" | ") {
                "Ø${it.diameter.mm}: ${it.weightKg} كجم (${it.bars12mRounded} سيخة)"
            }
            val summary = "خرسانة: ${totals.totalConcreteGrossM3} م³ | أسمنت: ${totals.cementBags50Kg} شوال | حديد: ${totals.estimatedTotalSteelTonnes} طن${if (steelBreakdownSummary.isNotBlank()) " ($steelBreakdownSummary)" else ""} | طوب: ${totals.totalBricksThousand} ألف"
            val details = buildString {
                appendLine("المصدر: ${s.selectedFileName.ifBlank { "نص كراسة مواصفات" }}")
                appendLine("المشروع: ${report.technicalSummary.projectTitle}")
                appendLine("الجهة/الاستشاري: ${report.technicalSummary.clientOrConsultant}")
                appendLine("مواصفات الخرسانة: ${report.technicalSummary.concreteMixSpecsAr}")
                appendLine("نوع الأسمنت: ${report.technicalSummary.cementTypeAr}")
                appendLine("مواصفات الحديد: ${report.technicalSummary.steelGradeAndDiametersAr}")
                appendLine("الغطاء الخرساني: ${report.technicalSummary.concreteCoverSpecAr}")
                appendLine("--- الملخص السوقي بالسودان ---")
                appendLine("• الحفر والتأسيس: ${totals.excavationM3} م³")
                appendLine("• الخرسانة الكلية: ${totals.totalConcreteGrossM3} م³")
                appendLine("• الأسمنت: ${totals.cementBags50Kg} شوال (${totals.cementTonnes} طن)")
                appendLine("• الرمل: ${totals.sandM3} م³ (${totals.sandTataTrucks} لوري تاتة)")
                appendLine("• الكنكري: ${totals.gravelM3} م³ (${totals.gravelTataTrucks} لوري تاتة)")
                appendLine("• الحديد الإجمالي: ${totals.estimatedTotalSteelTonnes} طن (${totals.estimatedTotalSteelKg} كجم)")
                totals.steelByDiameter.forEach { dRow ->
                    appendLine("  - قطر ${dRow.diameter.mm} مم: ${dRow.weightKg} كجم (${dRow.weightTonnes} طن) | ${dRow.bars12mRounded} سيخة 12م")
                }
                appendLine("• المباني: ${totals.totalBricksCount} طوبة (${totals.totalBricksThousand} ألف)")
                appendLine("• التشطيبات: ${totals.finishingAreaM2} م² | العزل: ${totals.waterproofingAreaM2} م²")
                appendLine("\n--- بنود كراسة المواصفات ---")
                report.items.filter { it.isIncluded }.forEach { item ->
                    appendLine("- [${item.itemCode}] ${item.nameAr} (${item.category.arLabel}): ${item.specRequirementAr}")
                }
            }
            val prices = _marketPrices.value
            val estCost = (totals.cementTonnes * prices.cementTonPrice) +
                (totals.sandTataTrucks * prices.sandTataTruckPrice) +
                (totals.gravelTataTrucks * prices.gravelTataTruckPrice) +
                (totals.estimatedTotalSteelTonnes * prices.steelTonPrice) +
                (totals.totalBricksThousand * prices.redBricksPerThousand) +
                (totals.finishingAreaM2 * prices.plasterPerM2Price)

            repository.saveProject(
                ProjectEntity(
                    title = title.ifBlank { report.technicalSummary.projectTitle },
                    clientOrLocation = location.ifBlank { "السودان" },
                    category = "كراسة مواصفات",
                    summaryText = summary,
                    detailedDetails = details,
                    totalCostSdg = estCost
                )
            )
            _snackMessage.emit("تم حفظ كميات كراسة المواصفات في المشاريع المحفوظة!")
            onSaved()
        }
    }

    fun createWorkPlanFromSpecDocument(
        title: String,
        location: String,
        pace: SchedulePace,
        startDateMillis: Long = System.currentTimeMillis(),
        onCreated: () -> Unit = {}
    ) {
        val s = _specState.value
        val report = s.report ?: return
        val totals = s.aggregatedTotals
        viewModelScope.launch {
            val (plan, phases) = WorkPlanGenerator.generateFromSpecDocument(
                title = title,
                location = location,
                pace = pace,
                startDateMillis = startDateMillis,
                report = report,
                concreteGrossM3 = totals.totalConcreteGrossM3,
                steelTonnes = totals.estimatedTotalSteelTonnes,
                steelByDiameter = totals.steelByDiameter,
                bricksCount = totals.totalBricksCount,
                finishingAreaM2 = totals.finishingAreaM2,
                excavationM3 = totals.excavationM3,
                waterproofingAreaM2 = totals.waterproofingAreaM2
            )
            val newId = repository.createWorkPlan(plan, phases)
            _selectedWorkPlanId.value = newId
            _snackMessage.emit("تم إنشاء خطة العمل الزمنية من كراسة المواصفات")
            onCreated()
        }
    }

    // --- WORK PLAN & FLEXIBLE TIMELINE ACTIONS ---

    fun selectWorkPlan(planId: Long) {
        _selectedWorkPlanId.value = planId
    }

    fun createWorkPlanFromCurrentCalculations(
        title: String,
        location: String,
        pace: SchedulePace,
        startDateMillis: Long,
        onCreated: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val sSteel = _steelState.value
            val steelTonnes = if (sSteel.isDetailedMode) {
                sSteel.detailedResult?.totalWeightTonnes ?: 0.0
            } else {
                sSteel.empiricalTotalTonnes
            }
            val steelDiaMm = if (sSteel.isDetailedMode) sSteel.diameter.mm else sSteel.empiricalDiameter.mm
            val steelBars = if (sSteel.isDetailedMode) {
                sSteel.detailedResult?.main12mBarsRounded ?: 0
            } else {
                sSteel.empiricalPrimaryBreakdown?.bars12mRounded ?: 0
            }
            val bpTotals = _blueprintState.value.aggregatedTotals

            val (plan, phases) = WorkPlanGenerator.generateFromCurrentCalculations(
                title = title,
                location = location,
                pace = pace,
                startDateMillis = startDateMillis,
                concreteResult = _concreteState.value.result,
                concreteElement = _concreteState.value.elementType,
                steelTonnes = steelTonnes,
                steelDiameterMm = steelDiaMm,
                steelBars12m = steelBars,
                masonryResult = _masonryState.value.result,
                plasterResult = _finishingState.value.plasterResult,
                tilesResult = _finishingState.value.tilesResult,
                paintResult = _finishingState.value.paintResult,
                blueprintConcreteM3 = bpTotals.totalConcreteGrossM3,
                blueprintSteelTonnes = bpTotals.estimatedTotalSteelTonnes,
                blueprintBricksCount = bpTotals.totalBricksCount,
                blueprintFinishAreaM2 = bpTotals.finishingAreaM2
            )
            val newId = repository.createWorkPlan(plan, phases)
            _selectedWorkPlanId.value = newId
            _snackMessage.emit("تم توليد خطة العمل والخارطة الزمنية بنجاح")
            onCreated()
        }
    }

    fun createOrOpenWorkPlanForSavedProject(
        project: ProjectEntity,
        pace: SchedulePace = SchedulePace.STANDARD,
        navigateToSchedule: Boolean = true
    ) {
        viewModelScope.launch {
            val existing = repository.getPlanByLinkedProjectId(project.id)
            if (existing != null) {
                _selectedWorkPlanId.value = existing.plan.id
                if (navigateToSchedule) navigateTo(AppScreen.WORK_PLAN)
                _snackMessage.emit("تم فتح الخطة الزمنية المرتبطة بالمشروع")
                return@launch
            }
            val (plan, phases) = WorkPlanGenerator.generateFromSavedProject(
                project = project,
                pace = pace,
                startDateMillis = System.currentTimeMillis()
            )
            val newId = repository.createWorkPlan(plan, phases)
            _selectedWorkPlanId.value = newId
            if (navigateToSchedule) navigateTo(AppScreen.WORK_PLAN)
            _snackMessage.emit("تم إنشاء خطة عمل مرنة لمشروع \"${project.title}\"")
        }
    }

    fun createManualWorkPlan(
        title: String,
        location: String,
        floorsCount: Int,
        areaPerFloorM2: Double,
        template: ManualProjectTemplate,
        pace: SchedulePace,
        startDateMillis: Long,
        startBlank: Boolean,
        onCreated: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val (plan, phases) = WorkPlanGenerator.generateManualPlan(
                title = title,
                location = location,
                floorsCount = floorsCount,
                areaPerFloorM2 = areaPerFloorM2,
                template = template,
                pace = pace,
                startDateMillis = startDateMillis,
                startBlank = startBlank
            )
            val newId = repository.createWorkPlan(plan, phases)
            _selectedWorkPlanId.value = newId
            _snackMessage.emit("تم إنشاء خطة العمل اليدوية بنجاح")
            onCreated()
        }
    }

    fun updateWorkPlanMeta(
        plan: WorkPlanEntity,
        title: String,
        location: String,
        startDateMillis: Long,
        notes: String
    ) {
        viewModelScope.launch {
            repository.updateWorkPlan(
                plan.copy(
                    title = title.ifBlank { plan.title },
                    location = location.ifBlank { plan.location },
                    startDateMillis = startDateMillis,
                    notes = notes
                )
            )
            _snackMessage.emit("تم تحديث بيانات الخطة الزمنية")
        }
    }

    fun changeWorkPlanPace(planWithPhases: WorkPlanWithPhases, newPace: SchedulePace) {
        viewModelScope.launch {
            val oldPace = SchedulePace.fromName(planWithPhases.plan.paceName)
            val rescaled = WorkPlanGenerator.rescalePlanPace(
                phasesSorted = planWithPhases.orderedPhases,
                oldPace = oldPace,
                newPace = newPace
            )
            repository.updateWorkPlan(planWithPhases.plan.copy(paceName = newPace.name))
            repository.updateWorkPlanPhases(rescaled)
            _snackMessage.emit("تم تكييف الخارطة الزمنية للوتيرة: ${newPace.arLabel}")
        }
    }

    fun deleteWorkPlan(planId: Long) {
        viewModelScope.launch {
            repository.deleteWorkPlan(planId)
            if (_selectedWorkPlanId.value == planId) {
                _selectedWorkPlanId.value = workPlans.value.firstOrNull { it.plan.id != planId }?.plan?.id
            }
            _snackMessage.emit("تم حذف خطة العمل")
        }
    }

    fun addWorkPlanPhase(
        planWithPhases: WorkPlanWithPhases,
        title: String,
        category: PhaseCategory,
        startDay: Int,
        durationDays: Int,
        quantityHint: String,
        resourcesNote: String
    ) {
        viewModelScope.launch {
            val ordered = planWithPhases.orderedPhases
            val nextOrder = (ordered.maxOfOrNull { it.sortOrder } ?: -1) + 1
            repository.addWorkPlanPhase(
                WorkPlanPhaseEntity(
                    planId = planWithPhases.plan.id,
                    sortOrder = nextOrder,
                    title = title.ifBlank { "مرحلة تنفيذية جديدة" },
                    categoryName = category.name,
                    startDay = startDay.coerceAtLeast(0),
                    durationDays = durationDays.coerceAtLeast(1),
                    progressPercent = 0,
                    statusName = PhaseStatus.NOT_STARTED.name,
                    quantityHint = quantityHint,
                    resourcesNote = resourcesNote
                )
            )
            _snackMessage.emit("تمت إضافة المرحلة إلى الخارطة الزمنية")
        }
    }

    fun updateWorkPlanPhase(
        planWithPhases: WorkPlanWithPhases,
        updatedPhase: WorkPlanPhaseEntity,
        autoShiftSubsequent: Boolean
    ) {
        viewModelScope.launch {
            val updatedList = WorkPlanGenerator.updatePhaseFlexible(
                allPhasesSorted = planWithPhases.orderedPhases,
                updatedPhase = updatedPhase,
                autoShiftSubsequent = autoShiftSubsequent
            )
            repository.updateWorkPlanPhases(updatedList)
        }
    }

    fun shiftWorkPlanPhase(
        planWithPhases: WorkPlanWithPhases,
        phase: WorkPlanPhaseEntity,
        deltaDays: Int,
        cascadeFollowing: Boolean
    ) {
        val newStart = (phase.startDay + deltaDays).coerceAtLeast(0)
        if (newStart == phase.startDay) return
        updateWorkPlanPhase(
            planWithPhases = planWithPhases,
            updatedPhase = phase.copy(startDay = newStart),
            autoShiftSubsequent = cascadeFollowing
        )
    }

    fun cycleWorkPlanPhaseStatus(phase: WorkPlanPhaseEntity) {
        viewModelScope.launch {
            val nextStatus = PhaseStatus.fromName(phase.statusName).nextCycle()
            val newProgress = when (nextStatus) {
                PhaseStatus.NOT_STARTED -> 0
                PhaseStatus.IN_PROGRESS -> if (phase.progressPercent in 1..99) phase.progressPercent else 40
                PhaseStatus.COMPLETED -> 100
                PhaseStatus.DELAYED -> phase.progressPercent.coerceIn(0, 99)
            }
            repository.updateWorkPlanPhase(
                phase.copy(
                    statusName = nextStatus.name,
                    progressPercent = newProgress
                )
            )
        }
    }

    fun moveWorkPlanPhaseOrder(planWithPhases: WorkPlanWithPhases, phaseId: Long, moveUp: Boolean) {
        viewModelScope.launch {
            val list = planWithPhases.orderedPhases.toMutableList()
            val idx = list.indexOfFirst { it.id == phaseId }
            if (idx == -1) return@launch
            val targetIdx = if (moveUp) idx - 1 else idx + 1
            if (targetIdx !in list.indices) return@launch
            val item = list.removeAt(idx)
            list.add(targetIdx, item)
            val reindexed = list.mapIndexed { order, entity -> entity.copy(sortOrder = order) }
            repository.updateWorkPlanPhases(reindexed)
        }
    }

    fun deleteWorkPlanPhase(phaseId: Long) {
        viewModelScope.launch {
            repository.deleteWorkPlanPhase(phaseId)
            _snackMessage.emit("تم حذف المرحلة من الجدول الزمني")
        }
    }

    private fun resolveDisplayName(context: Application, uri: Uri): String {
        var name = "file"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx != -1 && cursor.moveToFirst()) {
                name = cursor.getString(idx) ?: name
            }
        }
        return name
    }

    companion object {
        fun formatSdg(amount: Double): String {
            val format = NumberFormat.getNumberInstance(Locale.US)
            format.maximumFractionDigits = 0
            return format.format(amount)
        }
    }
}
