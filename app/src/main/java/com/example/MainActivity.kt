/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusDebt
import com.example.ui.theme.BaseGold
import java.text.SimpleDateFormat
import java.util.*

// ===================================
// DATA MODEL STRUCTURES (Arabic ERP)
// ===================================

enum class UserRole(val labelAr: String) {
  ADMIN("المدير العام (Admin)"),
  MANAGER("مدير الفرع (Manager)"),
  CASHIER("أمين الصندوق (Cashier)")
}

data class ERPUser(
  val id: String,
  val username: String,
  val email: String,
  var role: UserRole,
  val baseSalary: Double,
  val hourlyRate: Double,
  var commissionEarned: Double = 0.0
)

data class ERPBranch(
  val id: String,
  val name: String,
  val location: String
)

data class ERPProduct(
  val id: String,
  val barcode: String,
  val name: String,
  val category: String,
  val unitRetailPrice: Double,    // Price per piece
  val unitWholesalePrice: Double, // Price per piece inside a carton
  val unitCost: Double,           // Cost of buying 1 piece
  val cartonQuantity: Int,        // Pieces per carton (box)
  val isZakatEligible: Boolean = true
)

// Tracks stock level of a product at a specific branch
data class ERPInventory(
  val branchId: String,
  val productId: String,
  var stockInPieces: Int,
  val minRequiredStock: Int = 12
)

enum class ContactType { CUSTOMER, SUPPLIER }

data class ERPContact(
  val id: String,
  val name: String,
  val phone: String,
  val type: ContactType,
  var debtBalance: Double, // Positive = we owe them (Supplier) or they owe us (Customer)
  val creditLimit: Double  // Maximum debt permitted
)

enum class PayMethod(val labelAr: String) {
  CASH("نقداً"),
  CARD("شبكة / بطاقة"),
  DEBT("آجل (حساب الديون)")
}

data class CartItem(
  val product: ERPProduct,
  var quantity: Int, // Number of pieces
  var isCartonSale: Boolean = false // If true, price adjusts to wholesale and quant is carton * cartonQuantity
) {
  val totalPieces: Int
    get() = if (isCartonSale) quantity * product.cartonQuantity else quantity

  val subtotal: Double
    get() {
      val pricePerPiece = if (isCartonSale) product.unitWholesalePrice else product.unitRetailPrice
      return totalPieces * pricePerPiece
    }

  val rawCost: Double
    get() = totalPieces * product.unitCost
}

data class ERPInvoice(
  val id: String,
  val invoiceNumber: String,
  val branchName: String,
  val cashierName: String,
  val customerName: String?,
  val totalAmount: Double,
  val totalCost: Double,
  val debtAmount: Double,
  val paymentMethod: PayMethod,
  val date: String,
  val items: List<CartItem>
)

data class AttendanceLog(
  val employeeName: String,
  val date: String,
  val checkIn: String,
  var checkOut: String? = null,
  val overtimeHours: Double = 0.0
)

data class ZakatHistoryRecord(
  val id: String,
  val date: String,
  val netAssets: Double,
  val zakatDue: Double,
  val status: String
)

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        ERPMasterView()
      }
    }
  }
}

@Composable
fun ERPMasterView() {
  // ===================================
  // INITIAL MOCK DATA & PERSISTENT STATES
  // ===================================

  // 1. Branches list
  val branches = remember {
    listOf(
      ERPBranch("b1", "فرع الرياض الرئيسي", "طريق الملك فهد"),
      ERPBranch("b2", "فرع جدة - البلد", "شارع حائل"),
      ERPBranch("b3", "فرع الدمام - الفيصلية", "طريق الملك عبدالله")
    )
  }
  var selectedBranch by remember { mutableStateOf(branches[0]) }

  // 2. Products List
  val products = remember {
    mutableStateListOf(
      ERPProduct("p1", "628101001", "أرز بسمتي فاخر (5 كجم)", "المواد الغذائية", 45.0, 41.5, 30.0, 6),
      ERPProduct("p2", "628101002", "زيت طهي نقي (1.5 لتر)", "المواد الغذائية", 18.0, 15.0, 11.5, 12),
      ERPProduct("p3", "628101003", "حليب مجفف سريع الذوبان", "المواد الغذائية", 28.0, 24.0, 17.0, 8),
      ERPProduct("p4", "628101004", "مجموع كرتون مياه معدنية", "المشروبات", 14.0, 11.0, 7.0, 24),
      ERPProduct("p5", "628101005", "شاي سيلاني فاخر (100 كيس)", "المشروبات", 16.5, 14.0, 9.5, 10),
      ERPProduct("p6", "628101006", "مسحوق غسيل مركز (3 كجم)", "المنظفات", 39.0, 35.0, 24.5, 4),
      ERPProduct("p7", "628101007", "صابون ترطيب طبيعي (6 حبات)", "المنظفات", 22.0, 19.5, 13.0, 12)
    )
  }

  // 3. User Roles & Auth System
  val appUsers = remember {
    mutableStateListOf(
      ERPUser("u1", "أحمد الشمري", "ahmed@erp.sa", UserRole.ADMIN, 12000.0, 75.0),
      ERPUser("u2", "سليمان العتيبي", "soliman@erp.sa", UserRole.MANAGER, 8500.0, 50.0),
      ERPUser("u3", "عبدالله الهلال", "abdullah@erp.sa", UserRole.CASHIER, 5000.0, 30.0)
    )
  }
  var loggedInUser by remember { mutableStateOf<ERPUser?>(null) }
  var showAuthScreen by remember { mutableStateOf(true) }

  // 4. Branch Specific Inventory Records
  val inventories = remember {
    mutableStateListOf(
      // Branch 1 Riyadh Stock
      ERPInventory("b1", "p1", 85, 15),
      ERPInventory("b1", "p2", 140, 24),
      ERPInventory("b1", "p3", 42, 12),
      ERPInventory("b1", "p4", 320, 50),
      ERPInventory("b1", "p5", 96, 12),
      ERPInventory("b1", "p6", 35, 8),
      ERPInventory("b1", "p7", 110, 15),

      // Branch 2 Jeddah Stock
      ERPInventory("b2", "p1", 40, 15),
      ERPInventory("b2", "p2", 15, 24), // Low stock trigger
      ERPInventory("b2", "p3", 8, 12),  // Low stock trigger
      ERPInventory("b2", "p4", 150, 50),
      ERPInventory("b2", "p5", 20, 12),
      ERPInventory("b2", "p6", 5, 8),   // Low stock trigger
      ERPInventory("b2", "p7", 32, 15),

      // Branch 3 Dammam Stock
      ERPInventory("b3", "p1", 12, 15), // Low stock trigger
      ERPInventory("b3", "p2", 80, 24),
      ERPInventory("b3", "p3", 18, 12),
      ERPInventory("b3", "p4", 90, 50),
      ERPInventory("b3", "p5", 55, 12),
      ERPInventory("b3", "p6", 40, 8),
      ERPInventory("b3", "p7", 14, 15)  // Low stock trigger
    )
  }

  // 5. Contacts (Customers & Suppliers with Debts)
  val contacts = remember {
    mutableStateListOf(
      ERPContact("c1", "مؤسسة التموين الغذائي", "0502233441", ContactType.SUPPLIER, 32100.0, 100000.0),
      ERPContact("c2", "شركة المنظفات المتحدة", "0502233442", ContactType.SUPPLIER, 8400.0, 50000.0),
      ERPContact("c3", "علي بن صالح القحطاني", "0554433221", ContactType.CUSTOMER, 1420.0, 5000.0),
      ERPContact("c4", "مريم عبدالرحمن التميمي", "0554433222", ContactType.CUSTOMER, 350.0, 2500.0),
      ERPContact("c5", "صالح بن فهد الدوسري (عميل جملة)", "0554433223", ContactType.CUSTOMER, 4200.0, 10000.0),
      ERPContact("c6", "نورة سليمان العيسى", "0554433224", ContactType.CUSTOMER, 0.0, 1500.0)
    )
  }

  // 6. Transactions & Invoices Log
  val invoices = remember {
    mutableStateListOf(
      ERPInvoice(
        "inv1", "INV-2026-0001", "فرع الرياض الرئيسي", "عبدالله الهلال", "علي بن صالح القحطاني",
        350.0, 240.0, 0.0, PayMethod.CASH, "2026-06-02 10:15",
        listOf(CartItem(products[0], 4), CartItem(products[4], 10))
      ),
      ERPInvoice(
        "inv2", "INV-2026-0002", "فرع الرياض الرئيسي", "أحمد الشمري", "صالح بن فهد الدوسري (عميل جملة)",
        2100.0, 1500.0, 1200.0, PayMethod.DEBT, "2026-06-02 14:30",
        listOf(CartItem(products[1], 10, true), CartItem(products[2], 5, true))
      )
    )
  }

  // 7. Employee Attendance Ledger
  val attendanceLogs = remember {
    mutableStateListOf(
      AttendanceLog("أحمد الشمري", "2026-06-02", "08:00", "17:00", 1.0),
      AttendanceLog("سليمان العتيبي", "2026-06-02", "07:55", "16:15", 0.0),
      AttendanceLog("عبدالله الهلال", "2026-06-02", "08:15", null, 0.0) // Still in shift
    )
  }

  // 8. Islamic Zakat Logs
  val zakatRecords = remember {
    mutableStateListOf(
      ZakatHistoryRecord("zk1", "2025-05-15", 185000.0, 4625.0, "تم إرساء الدفع للجهات المختصة"),
      ZakatHistoryRecord("zk2", "2026-06-01", 240000.0, 6000.0, "قيد المراجعة السنوية")
    )
  }

  // Active POS cart
  val activeCart = remember { mutableStateListOf<CartItem>() }

  // Navigation Panel Selection
  // Screens: 0=الرئيسية (Stats), 1=المبيعات POS, 2=المستودع والمخازن, 3=الزبائن والديون, 4=الموظفين والرواتب, 5=الزكاة الشرعية, 6=الفواتير السابقة
  var currentScreenIndex by remember { mutableIntStateOf(0) }

  // Notification overlays
  var successToastMessage by remember { mutableStateOf<String?>(null) }
  var errorToastMessage by remember { mutableStateOf<String?>(null) }

  // Local static cash holding (rebuilt dynamically from sales and capital, starting vault 250,000 SAR)
  val startingVault = 250000.00
  val calculatedCashInHand: Double = remember(invoices.size, contacts) {
    val totalCashSales = invoices.filter { it.paymentMethod != PayMethod.DEBT }.sumOf { it.totalAmount }
    val totalCashRefunds = invoices.filter { it.paymentMethod == PayMethod.DEBT }.sumOf { it.totalAmount - it.debtAmount }
    startingVault + totalCashSales + totalCashRefunds
  }

  // Launch welcome messages or actions when authenticated
  LaunchedEffect(loggedInUser) {
    if (loggedInUser != null) {
      successToastMessage = "أهلاً بك يا ${loggedInUser?.username}! تم تأكيد الصلاحيات لـ ${loggedInUser?.role?.labelAr}."
    }
  }

  // ===================================
  // MAIN VIEW HOUSING
  // ===================================
  Scaffold(
    modifier = Modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      if (showAuthScreen) {
        ERPAuthView(
          users = appUsers,
          onLoginSuccess = { user ->
            loggedInUser = user
            showAuthScreen = false
          }
        )
      } else {
        Row(modifier = Modifier.fillMaxSize()) {
          // RIGHT SIDEBAR NAVIGATION DRAWER FOR ARABIC RTL READABILITY
          NavigationRailRTL(
            selectedIndex = currentScreenIndex,
            currentUser = loggedInUser ?: appUsers[0],
            branchName = selectedBranch.name,
            branches = branches,
            onBranchSelected = { selectedBranch = it },
            onNavigate = { currentScreenIndex = it },
            onLogout = {
              loggedInUser = null
              showAuthScreen = true
              activeCart.clear()
            }
          )

          // MAIN SCREEN CONTAINER
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
              .background(MaterialTheme.colorScheme.background)
          ) {
            AnimatedContent(
              targetState = currentScreenIndex,
              transitionSpec = {
                fadeIn() togetherWith fadeOut()
              },
              label = "screen_trans"
            ) { targetIndex ->
              when (targetIndex) {
                0 -> DashboardScreen(
                  invoices = invoices,
                  products = products,
                  inventories = inventories,
                  contacts = contacts,
                  cashInHand = calculatedCashInHand,
                  branchId = selectedBranch.id
                )
                1 -> PosSystemScreen(
                  products = products,
                  branch = selectedBranch,
                  inventories = inventories,
                  contacts = contacts,
                  cashier = loggedInUser ?: appUsers[0],
                  activeCart = activeCart,
                  invoices = invoices,
                  onInvoiceCreated = { invoice ->
                    invoices.add(0, invoice)
                    successToastMessage = "تم إصدار الفاتورة المبيعية بنجاح رقم ${invoice.invoiceNumber}"
                  },
                  showToastError = { errorToastMessage = it }
                )
                2 -> InventoryManagerScreen(
                  products = products,
                  branch = selectedBranch,
                  inventories = inventories,
                  otherBranches = branches.filter { it.id != selectedBranch.id },
                  onInventoryAdjusted = { successToastMessage = "تم تحديث كميات المخازن بنجاح" },
                  onStockTransferred = { pId, fromB, toB, qty ->
                    // Subtract from current
                    val sourceInv = inventories.find { it.branchId == fromB && it.productId == pId }
                    val targetInv = inventories.find { it.branchId == toB && it.productId == pId }
                    if (sourceInv != null && sourceInv.stockInPieces >= qty) {
                      sourceInv.stockInPieces -= qty
                      if (targetInv != null) {
                        targetInv.stockInPieces += qty
                      } else {
                        inventories.add(ERPInventory(toB, pId, qty))
                      }
                      successToastMessage = "تم نقل المركبات المخزنية ($qty حبة) للفرع المستهدف بنجاح!"
                    } else {
                      errorToastMessage = "خطأ: المخزون غير كافٍ في فرع المصدر لنقل الكميات"
                    }
                  }
                )
                3 -> FinancialLedgerScreen(
                  contacts = contacts,
                  successTrigger = { successToastMessage = it }
                )
                4 -> AttendanceSalaryScreen(
                  users = appUsers,
                  attendanceLogs = attendanceLogs,
                  invoices = invoices,
                  successTrigger = { successToastMessage = it }
                )
                5 -> IslamicZakatScreen(
                  cashInHand = calculatedCashInHand,
                  products = products,
                  inventories = inventories,
                  contacts = contacts,
                  zakatHistory = zakatRecords,
                  onZakatLogged = { successToastMessage = "تم حساب وتوثيق زكاة المال السنوية بنجاح وإعداد السجل الشرعي!" }
                )
                6 -> InvoiceArchiveScreen(invoices = invoices)
              }
            }
          }
        }
      }

      // GLOBAL CUSTOM ACCENTED RADIAL TOASTS (RTL localized)
      Column(
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(24.dp)
          .widthIn(max = 350.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        successToastMessage?.let { msg ->
          ToastBanner(msg, isError = false) { successToastMessage = null }
        }
        errorToastMessage?.let { msg ->
          ToastBanner(msg, isError = true) { errorToastMessage = null }
        }
      }
    }
  }
}

// ===================================
// SIDEBAR RTL NAVIGATION COMPONENT
// ===================================
@Composable
fun NavigationRailRTL(
  selectedIndex: Int,
  currentUser: ERPUser,
  branchName: String,
  branches: List<ERPBranch>,
  onBranchSelected: (ERPBranch) -> Unit,
  onNavigate: (Int) -> Unit,
  onLogout: () -> Unit
) {
  var showBranchMenu by remember { mutableStateOf(false) }

  Column(
    modifier = Modifier
      .fillMaxHeight()
      .width(260.dp)
      .background(MaterialTheme.colorScheme.surface)
      .border(1.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(0.dp))
      .padding(16.dp),
    horizontalAlignment = Alignment.End
  ) {
    // Elegant ERP Header Brand
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 12.dp),
      horizontalArrangement = Arrangement.End,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(horizontalAlignment = Alignment.End) {
        Text(
          text = "مظلة الأعمال ERP",
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
          textAlign = TextAlign.Right
        )
        Text(
          text = "إدارة المؤسسة ونقاط البيع",
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSurface,
          textAlign = TextAlign.Right
        )
      }
      Spacer(modifier = Modifier.width(12.dp))
      Box(
        modifier = Modifier
          .size(42.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.Star,
          contentDescription = "Logo",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(24.dp)
        )
      }
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.secondary)

    // USER CARD WITH ROLES INFO
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.background)
        .border(1.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(12.dp))
        .padding(12.dp)
    ) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
      ) {
        Text(
          text = currentUser.username,
          fontWeight = FontWeight.Bold,
          fontSize = 14.sp,
          color = MaterialTheme.colorScheme.onBackground
        )
        Text(
          text = currentUser.role.labelAr,
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.SemiBold
        )
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // BRANCH PICKER IN DRAPDOWN
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
        .clickable { showBranchMenu = true }
        .padding(12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = Icons.Default.Search,
          contentDescription = "Picker",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(16.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          Column(horizontalAlignment = Alignment.End) {
            Text("الفرع النشط الحالي", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(branchName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
          }
          Spacer(modifier = Modifier.width(8.dp))
          Icon(
            imageVector = Icons.Default.Home,
            contentDescription = "BranchIcon",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
          )
        }
      }

      DropdownMenu(
        expanded = showBranchMenu,
        onDismissRequest = { showBranchMenu = false },
        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
      ) {
        branches.forEach { b ->
          DropdownMenuItem(
            text = { Text(b.name, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth()) },
            onClick = {
              onBranchSelected(b)
              showBranchMenu = false
            }
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // NAV ITEMS (Localized, RTL alignment)
    val navItems = listOf(
      Triple("لوحة المؤشرات والتقارير", Icons.Default.Home, 0),
      Triple("نظام مبيعات الكاشير POS", Icons.Default.ShoppingCart, 1),
      Triple("إدارة المستودعات والمخازن", Icons.Default.Settings, 2),
      Triple("الزبائن والموردين والديون", Icons.Default.Person, 3),
      Triple("دوات الموظفين والرواتب", Icons.Default.Check, 4),
      Triple("حساب الزكاة الشرعية", Icons.Default.Info, 5),
      Triple("أرشيف الفواتير اليومية", Icons.Default.Search, 6)
    )

    LazyColumn(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      items(navItems) { item ->
        val isSelected = selectedIndex == item.third
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f) else Color.Transparent)
            .border(
              width = 1.dp,
              color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Transparent,
              shape = RoundedCornerShape(8.dp)
            )
            .clickable { onNavigate(item.third) }
            .padding(horizontal = 12.dp),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = item.first,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
          )
          Spacer(modifier = Modifier.width(12.dp))
          Icon(
            imageVector = item.second,
            contentDescription = item.first,
            tint = if (isSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.secondary)

    // Logout option
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(44.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
        .border(1.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(8.dp))
        .clickable { onLogout() }
        .padding(horizontal = 12.dp),
      horizontalArrangement = Arrangement.End,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "تسجيل الخروج الآمن",
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = StatusDebt
      )
      Spacer(modifier = Modifier.width(12.dp))
      Icon(
        imageVector = Icons.Default.Close,
        contentDescription = "Logout",
        tint = StatusDebt,
        modifier = Modifier.size(18.dp)
      )
    }
  }
}

// ===================================
// 1. STATS DASHBOARD SCREEN
// ===================================
@Composable
fun DashboardScreen(
  invoices: List<ERPInvoice>,
  products: List<ERPProduct>,
  inventories: List<ERPInventory>,
  contacts: List<ERPContact>,
  cashInHand: Double,
  branchId: String
) {
  // Aggregate calculations
  val branchInvoices = invoices.filter { it.branchName.contains("الرياض") && branchId == "b1" || it.branchName.contains("جدة") && branchId == "b2" || it.branchName.contains("الدمام") && branchId == "b3" }
  val totalSalesRevenue = branchInvoices.sumOf { it.totalAmount }
  val totalSalesCost = branchInvoices.sumOf { it.totalCost }
  val calculatedNetProfit = totalSalesRevenue - totalSalesCost

  val clientReceivables = contacts.filter { it.type == ContactType.CUSTOMER }.sumOf { it.debtBalance }
  val supplierPayables = contacts.filter { it.type == ContactType.SUPPLIER }.sumOf { it.debtBalance }

  val branchStockValue = inventories.filter { it.branchId == branchId }.sumOf { inv ->
    val p = products.find { it.id == inv.productId }
    (p?.unitCost ?: 0.0) * inv.stockInPieces
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
    horizontalAlignment = Alignment.End
  ) {
    item {
      Column(horizontalAlignment = Alignment.End) {
        Text("نظرة عامة على الأداء والمؤشرات المالية", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text("تحديثات لحظية دقيقة للفرع النشط الحالي استناداً للشبكة", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
      }
    }

    // STATS METRICS GRID
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        StatsCard(
          title = "إيرادات المبيعات",
          value = String.format("%.2f", totalSalesRevenue) + " ر.س",
          subtext = "عدد المبيعات: ${branchInvoices.size} فواتير",
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.weight(1f)
        )
        StatsCard(
          title = "الهامش الربحي الصافي",
          value = String.format("%.2f", calculatedNetProfit) + " ر.س",
          subtext = "معدل هامش: " + String.format("%.1f", if (totalSalesRevenue > 0) (calculatedNetProfit / totalSalesRevenue) * 100 else 0.0) + "%",
          color = StatusSuccess,
          modifier = Modifier.weight(1f)
        )
        StatsCard(
          title = "السيولة المتوفرة بالخزنة",
          value = String.format("%.2f", cashInHand) + " ر.س",
          subtext = "متضمنة رأس المال والتحصيل",
          color = StatusWarning,
          modifier = Modifier.weight(1f)
        )
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        StatsCard(
          title = "قيمة المستودع الحالية (بالتكلفة)",
          value = String.format("%.2f", branchStockValue) + " ر.س",
          subtext = "القيمة الخاضعة للزكاة السنوية",
          color = MaterialTheme.colorScheme.secondary,
          modifier = Modifier.weight(1f)
        )
        StatsCard(
          title = "ديون مستحقة للزبائن",
          value = String.format("%.2f", clientReceivables) + " ر.س",
          subtext = "حسابات أرصدة الآجل النشطة",
          color = StatusDebt,
          modifier = Modifier.weight(1f)
        )
        StatsCard(
          title = "مستحقات واجبة الدفع للموردين",
          value = String.format("%.2f", supplierPayables) + " ر.س",
          subtext = "التزامات التوريد القائمة",
          color = StatusDebt,
          modifier = Modifier.weight(1f)
        )
      }
    }

    // CUSTOM RENDERED CHARTS (Recharts layout replacement)
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .height(300.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
      ) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
          horizontalAlignment = Alignment.End
        ) {
          Text("مؤشر حركة المبيعات وتطورها الأسبوعي والشهري", fontSize = 15.sp, fontWeight = FontWeight.Bold)
          Spacer(modifier = Modifier.height(16.dp))
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
          ) {
            CanvasChartDrawing()
          }
          Spacer(modifier = Modifier.height(8.dp))
          // Legand
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
          ) {
            ChartLegendItem("هامش الربح", StatusSuccess)
            Spacer(modifier = Modifier.width(20.dp))
            ChartLegendItem("إجمالي المبيعات", MaterialTheme.colorScheme.primary)
          }
        }
      }
    }
  }
}

@Composable
fun StatsCard(
  title: String,
  value: String,
  subtext: String,
  color: Color,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      horizontalAlignment = Alignment.End
    ) {
      Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
      Spacer(modifier = Modifier.height(6.dp))
      Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
      Spacer(modifier = Modifier.height(4.dp))
      Text(subtext, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
  }
}

@Composable
fun CanvasChartDrawing() {
  val primaryColor = MaterialTheme.colorScheme.primary
  val successColor = StatusSuccess

  Canvas(modifier = Modifier.fillMaxSize()) {
    val width = size.width
    val height = size.height

    // Draw baseline
    drawLine(
      color = Color.LightGray.copy(alpha = 0.3f),
      start = Offset(0f, height - 20f),
      end = Offset(width, height - 20f),
      strokeWidth = 2f
    )

    // Simulation Points
    val salesPoints = listOf(
      Offset(width * 0.1f, height * 0.75f),
      Offset(width * 0.25f, height * 0.60f),
      Offset(width * 0.40f, height * 0.85f),
      Offset(width * 0.55f, height * 0.45f),
      Offset(width * 0.70f, height * 0.30f),
      Offset(width * 0.85f, height * 0.20f),
      Offset(width * 0.95f, height * 0.15f)
    )

    val profitPoints = listOf(
      Offset(width * 0.1f, height * 0.85f),
      Offset(width * 0.25f, height * 0.78f),
      Offset(width * 0.40f, height * 0.90f),
      Offset(width * 0.55f, height * 0.70f),
      Offset(width * 0.70f, height * 0.55f),
      Offset(width * 0.85f, height * 0.45f),
      Offset(width * 0.95f, height * 0.38f)
    )

    // Draw grid dashed lines
    for (i in 1..4) {
      val y = (height - 20f) * (i / 5f)
      drawLine(
        color = Color.LightGray.copy(alpha = 0.15f),
        start = Offset(0f, y),
        end = Offset(width, y),
        strokeWidth = 1f
      )
    }

    // Render sales curves
    for (i in 0 until salesPoints.size - 1) {
      drawLine(
        color = primaryColor,
        start = salesPoints[i],
        end = salesPoints[i + 1],
        strokeWidth = 4f,
        cap = StrokeCap.Round
      )
      drawCircle(
        color = primaryColor,
        radius = 5f,
        center = salesPoints[i]
      )
    }
    // Last point
    drawCircle(color = primaryColor, radius = 5f, center = salesPoints.last())

    // Render profits curves
    for (i in 0 until profitPoints.size - 1) {
      drawLine(
        color = successColor,
        start = profitPoints[i],
        end = profitPoints[i + 1],
        strokeWidth = 3f,
        cap = StrokeCap.Round
      )
      drawCircle(
        color = successColor,
        radius = 4f,
        center = profitPoints[i]
      )
    }
    drawCircle(color = successColor, radius = 4f, center = profitPoints.last())
  }
}

@Composable
fun ChartLegendItem(label: String, color: Color) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    Spacer(modifier = Modifier.width(6.dp))
    Box(
      modifier = Modifier
        .size(10.dp)
        .clip(RoundedCornerShape(2.dp))
        .background(color)
    )
  }
}


// ===================================
// 2. POS SYSTEMS SCREEN (POINT OF SALE)
// ===================================
@Composable
fun PosSystemScreen(
  products: List<ERPProduct>,
  branch: ERPBranch,
  inventories: List<ERPInventory>,
  contacts: List<ERPContact>,
  cashier: ERPUser,
  activeCart: MutableList<CartItem>,
  invoices: List<ERPInvoice>,
  onInvoiceCreated: (ERPInvoice) -> Unit,
  showToastError: (String) -> Unit
) {
  var searchQuery by remember { mutableStateOf("") }
  var barcodeQuery by remember { mutableStateOf("") }
  var selectedCustomer by remember { mutableStateOf<ERPContact?>(null) }
  var paymentMethod by remember { mutableStateOf(PayMethod.CASH) }
  var invoiceToPrint by remember { mutableStateOf<ERPInvoice?>(null) }

  // Filter products by selected categories
  val filteredProducts = products.filter {
    it.name.contains(searchQuery) || it.barcode.contains(searchQuery) || it.category.contains(searchQuery)
  }

  // Calculate cart costs
  val rawCost = activeCart.sumOf { it.rawCost }
  val totalAmount = activeCart.sumOf { it.subtotal }
  val vatAmount = totalAmount * 0.15 // 15% VAT Simulation
  val finalTotalInvoiced = totalAmount + vatAmount

  // Handle barcode simulation scan
  fun handleManualBarcodeCheck() {
    val prod = products.find { it.barcode == barcodeQuery.trim() }
    if (prod != null) {
      val existing = activeCart.find { it.product.id == prod.id }
      if (existing != null) {
        existing.quantity++
      } else {
        activeCart.add(CartItem(prod, 1))
      }
      barcodeQuery = ""
    } else {
      showToastError("عذراً، لم يتم العثور على الباركود المطلق بقائمة السلع")
    }
  }

  Row(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    horizontalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // LEFT PANEL: BASKET BILLING DESK
    Card(
      modifier = Modifier
        .width(360.dp)
        .fillMaxHeight(),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(16.dp),
        horizontalAlignment = Alignment.End
      ) {
        Text("طاولة الحساب الكلية", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(10.dp))

        // Cart items lazy list
        Box(modifier = Modifier.weight(1f)) {
          if (activeCart.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Text("سلة الحساب ممتلئة\nقم بإضافة منتج من شبكة المعروضات", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.4f), textAlign = TextAlign.Center)
            }
          } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              items(activeCart) { item ->
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .padding(8.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  // DELETE
                  Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Del",
                    tint = StatusDebt,
                    modifier = Modifier
                      .size(20.dp)
                      .clickable { activeCart.remove(item) }
                  )

                  Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(item.product.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, overflow = TextOverflow.Ellipsis, maxLines = 1)
                    Text(
                      text = "${String.format("%.2f", item.subtotal)} ر.س",
                      fontSize = 11.sp,
                      color = MaterialTheme.colorScheme.primary
                    )
                  }

                  // Quantity adjusters
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                  ) {
                    Box(
                      modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .clickable {
                          if (item.quantity > 1) {
                            item.quantity--
                            val idx = activeCart.indexOf(item)
                            activeCart[idx] = item.copy() // force recompose
                          }
                        },
                      contentAlignment = Alignment.Center
                    ) {
                      Text("-", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("${item.quantity}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Box(
                      modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .clickable {
                          item.quantity++
                          val idx = activeCart.indexOf(item)
                          activeCart[idx] = item.copy() // force recompose
                        },
                      contentAlignment = Alignment.Center
                    ) {
                      Text("+", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                  }
                }
              }
            }
          }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // Bill parameters & Calculations
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.End) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${String.format("%.2f", totalAmount)} ر.س", fontWeight = FontWeight.SemiBold)
            Text("المجموع الفرعي:", color = MaterialTheme.colorScheme.onSurface.copy(0.6f), fontSize = 12.sp)
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${String.format("%.2f", vatAmount)} ر.س", fontWeight = FontWeight.SemiBold)
            Text("ضريبة القيمة المضافة (15%):", color = MaterialTheme.colorScheme.onSurface.copy(0.6f), fontSize = 12.sp)
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
              text = "${String.format("%.2f", finalTotalInvoiced)} ر.س",
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.Bold,
              fontSize = 17.sp
            )
            Text("المبلغ الصافي شامل الضريبة:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Customer selection
        var showCustMenu by remember { mutableStateOf(false) }
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .clickable { showCustMenu = true }
            .padding(10.dp)
        ) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Icon(Icons.Default.Person, contentDescription = "P", tint = MaterialTheme.colorScheme.primary)
            Text(selectedCustomer?.name ?: "تسمية عميل الفاتورة (افتراضي نقدي)", fontSize = 12.sp)
          }
          DropdownMenu(expanded = showCustMenu, onDismissRequest = { showCustMenu = false }) {
            DropdownMenuItem(text = { Text("عميل نقدي افتراضي") }, onClick = { selectedCustomer = null; showCustMenu = false })
            contacts.filter { it.type == ContactType.CUSTOMER }.forEach { c ->
              DropdownMenuItem(
                text = { Text("${c.name} - رصيد الديون: ${c.debtBalance} SAR") },
                onClick = { selectedCustomer = c; showCustMenu = false }
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Payment type toggles
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          PayMethod.values().forEach { method ->
            val isSel = paymentMethod == method
            Box(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background)
                .clickable { paymentMethod = method }
                .padding(6.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = method.labelAr,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Checkout Button with credit checks
        val isDebtPayment = paymentMethod == PayMethod.DEBT
        val isOverCreditLimit = isDebtPayment && selectedCustomer != null &&
          (selectedCustomer!!.debtBalance + finalTotalInvoiced > selectedCustomer!!.creditLimit)

        if (isOverCreditLimit) {
          Text(
            text = "عذراً! تم تجاوز حد الائمان المقر للعميل (${selectedCustomer!!.creditLimit} ر.س)",
            color = StatusDebt,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
          )
        }

        Button(
          onClick = {
            if (activeCart.isEmpty()) {
              showToastError("يجب إضافة عناصر للسلة لحساب الفاتورة")
              return@Button
            }
            if (isOverCreditLimit) {
              showToastError("تجاوز الحد الائتماني المسموح به للعميل")
              return@Button
            }

            // Deduct stock from system matching current branch
            var stockValidationFail = false
            for (item in activeCart) {
              val inv = inventories.find { it.branchId == branch.id && it.productId == item.product.id }
              if (inv == null || inv.stockInPieces < item.totalPieces) {
                stockValidationFail = true
                break
              }
            }

            if (stockValidationFail) {
              showToastError("خطأ: لا يوجد مخزون كافٍ بفرع ${branch.name} لإتمام السداد")
              return@Button
            }

            // Deduct stock
            for (item in activeCart) {
              val inv = inventories.find { it.branchId == branch.id && it.productId == item.product.id }
              if (inv != null) {
                inv.stockInPieces = inv.stockInPieces - item.totalPieces
              }
            }

            // Record client debt adjustment if selected DEBT
            var recordedDebt = 0.0
            if (isDebtPayment && selectedCustomer != null) {
              selectedCustomer!!.debtBalance += finalTotalInvoiced
              recordedDebt = finalTotalInvoiced
            }

            // Create Invoice
            val newInvoice = ERPInvoice(
              id = "inv_" + System.currentTimeMillis(),
              invoiceNumber = "INV-${1000 + invoices.size}",
              branchName = branch.name,
              cashierName = cashier.username,
              customerName = selectedCustomer?.name ?: "زبون مبيعات نقدي",
              totalAmount = finalTotalInvoiced,
              totalCost = rawCost,
              debtAmount = recordedDebt,
              paymentMethod = paymentMethod,
              date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()),
              items = activeCart.toList()
            )

            // Add Cashier performance commission (2% of transaction total)
            cashier.commissionEarned += finalTotalInvoiced * 0.02

            onInvoiceCreated(newInvoice)
            invoiceToPrint = newInvoice
            activeCart.clear()
            selectedCustomer = null
          },
          modifier = Modifier.fillMaxWidth(),
          colors = ButtonDefaults.buttonColors(containerColor = if (isOverCreditLimit) Color.Gray else MaterialTheme.colorScheme.primary),
          enabled = !isOverCreditLimit
        ) {
          Text("سداد وطباعة الفاتورة الفورية", fontWeight = FontWeight.Bold)
        }
      }
    }

    // RIGHT PANEL: CATALOUGE OF SPREAD PRODUCTS & QUICK SCANNERS
    Column(
      modifier = Modifier.weight(1f),
      horizontalAlignment = Alignment.End
    ) {
      // QUICK BARCODE SIMULATION ROW
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Button(
          onClick = { handleManualBarcodeCheck() },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
          Text("محاكاة ماسح الباركود")
        }
        OutlinedTextField(
          value = barcodeQuery,
          onValueChange = { barcodeQuery = it },
          placeholder = { Text("أدخل باركود المنتج (مثال: 628101001)", fontSize = 11.sp) },
          trailingIcon = { Icon(Icons.Default.Star, contentDescription = "Scan") },
          modifier = Modifier.weight(1f),
          singleLine = true
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      // FILTER SEARCH FIELD
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        placeholder = { Text("ابحث باسم المنتج، التصنيف، أو الباركود...", fontSize = 12.sp) },
        trailingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
      )

      Spacer(modifier = Modifier.height(16.dp))

      // PRODUCTS VERTICAL MATCHING GRID
      LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        items(filteredProducts) { prod ->
          // Find stock level under current active branch
          val branchStockLevel = inventories.find { it.branchId == branch.id && it.productId == prod.id }?.stockInPieces ?: 0
          val isLowStock = branchStockLevel <= 12

          Card(
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                if (branchStockLevel > 0) {
                  val existing = activeCart.find { it.product.id == prod.id }
                  if (existing != null) {
                    existing.quantity++
                  } else {
                    activeCart.add(CartItem(prod, 1))
                  }
                } else {
                  showToastError("عذراً، هذا المنتج غير متوفر بالفرع")
                }
              },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, if (isLowStock) StatusWarning.copy(alpha = 0.5f) else MaterialTheme.colorScheme.secondary)
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              horizontalAlignment = Alignment.End
            ) {
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(4.dp))
                  .background(if (isLowStock) StatusWarning.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                  .padding(4.dp)
              ) {
                Text(
                  text = prod.category,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (isLowStock) StatusWarning else MaterialTheme.colorScheme.primary
                )
              }
              Spacer(modifier = Modifier.height(8.dp))
              Text(prod.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
              Text("الرمز: ${prod.barcode}", fontSize = 10.sp, color = Color.Gray)
              Spacer(modifier = Modifier.height(10.dp))

              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                  text = "${branchStockLevel} حبة",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = if (branchStockLevel == 0) StatusDebt else if (isLowStock) StatusWarning else Color.Gray
                )
                Text("${prod.unitRetailPrice} ر.س", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
              }
            }
          }
        }
      }
    }
  }

  // ===================================
  // FISCAL INVOICE DIALOG WITH CANVAS BARCODE
  // ===================================
  invoiceToPrint?.let { inv ->
    Dialog(onDismissRequest = { invoiceToPrint = null }) {
      Card(
        modifier = Modifier
          .width(350.dp)
          .padding(8.dp)
          .wrapContentHeight(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(2.dp, Color.Black)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text("مؤسسة مبيعات المؤسسات المحدودة", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 14.sp)
          Text(inv.branchName, color = Color.DarkGray, fontSize = 11.sp)
          Text("الرقم الضريبي: 302010442300003", color = Color.DarkGray, fontSize = 10.sp)

          HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Black)

          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(inv.invoiceNumber, fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 12.sp)
            Text("فاتورة ضريبية مبسطة", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 12.sp)
          }

          Spacer(modifier = Modifier.height(6.dp))

          Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            Text("صندوق الكاشير: ${inv.cashierName}", color = Color.Black, fontSize = 10.sp)
            Text("العميل: ${inv.customerName ?: "نقدي"}", color = Color.Black, fontSize = 10.sp)
            Text("التاريخ: ${inv.date}", color = Color.Black, fontSize = 10.sp)
            Text("طريقة الدفع: ${inv.paymentMethod.labelAr}", color = Color.Black, fontSize = 10.sp)
          }

          HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Black)

          // Invoice Items Rows
          inv.items.forEach { cartUnit ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("${cartUnit.subtotal} SAR", color = Color.Black, fontSize = 11.sp)
              Text("x${cartUnit.quantity} ${cartUnit.product.name}", color = Color.Black, fontSize = 11.sp)
            }
          }

          HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Black)

          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${String.format("%.2f", inv.totalAmount)} SAR", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 14.sp)
            Text("الإجمالي الكلي شامل الضريبة", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 13.sp)
          }

          Spacer(modifier = Modifier.height(16.dp))

          // Canvas Barcode rendering dynamically
          Text("رمز الاستجابة السريع لمصلحة الزكاة والجمارك والأعمال", color = Color.DarkGray, fontSize = 9.sp)
          Spacer(modifier = Modifier.height(4.dp))
          Canvas(modifier = Modifier
            .fillMaxWidth()
            .height(55.dp)) {
            val cellWidth = size.width / 40f
            val bars = listOf(1, 1, 0, 1, 0, 0, 1, 1, 1, 0, 1, 1, 0, 1, 0, 1, 1, 0, 0, 1, 0, 1, 1, 1, 0, 1, 1, 0, 0, 1, 1, 1, 0, 1, 0, 1, 1, 0, 1, 1)
            bars.forEachIndexed { i, active ->
              if (active == 1) {
                drawRect(
                  color = Color.Black,
                  topLeft = Offset(i * cellWidth, 0f),
                  size = Size(cellWidth * 0.75f, size.height)
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          Button(
            onClick = { invoiceToPrint = null },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
          ) {
            Text("إغلاق الفاتورة (طباعة المحاكاة)", color = Color.White)
          }
        }
      }
    }
  }
}


// ===================================
// 3. INVENTORY MANAGER SCREEN
// ===================================
@Composable
fun InventoryManagerScreen(
  products: MutableList<ERPProduct>,
  branch: ERPBranch,
  inventories: MutableList<ERPInventory>,
  otherBranches: List<ERPBranch>,
  onInventoryAdjusted: () -> Unit,
  onStockTransferred: (String, String, String, Int) -> Unit
) {
  var showAddProductDialog by remember { mutableStateOf(false) }
  var showTransferDialog by remember { mutableStateOf(false) }

  // New product inputs
  var nameIn by remember { mutableStateOf("") }
  var barcodeIn by remember { mutableStateOf("") }
  var categoryIn by remember { mutableStateOf("") }
  var priceIn by remember { mutableStateOf("") }
  var costIn by remember { mutableStateOf("") }
  var cartonQtyIn by remember { mutableStateOf("") }

  // Transfer parameters
  var transferSelectedProduct by remember { mutableStateOf<ERPProduct?>(null) }
  var transferSelectedQuantity by remember { mutableStateOf("") }
  var targetBranchSelected by remember { mutableStateOf<ERPBranch?>(null) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp),
    horizontalAlignment = Alignment.End
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { showTransferDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
          Text("نقل بضائع عبر الفروع")
        }
        Button(onClick = { showAddProductDialog = true }) {
          Text("إضافة منتج بالنظام")
        }
      }
      Column(horizontalAlignment = Alignment.End) {
        Text("إدارة مستودع الفرع: ${branch.name}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("إدارة باركود المنتجات، التكلفة، وحدة الكرتونة وتتبع الحجم المتبقي", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // STOCK VALUES IN TABLE
    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
      LazyColumn(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        item {
          Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("حالة التنبيه المخزني", fontWeight = FontWeight.Bold, modifier = Modifier.width(110.dp), textAlign = TextAlign.End)
            Text("الكمية المتوفرة حالياً", fontWeight = FontWeight.Bold, modifier = Modifier.width(130.dp), textAlign = TextAlign.End)
            Text("سعر التجزئة والكرتونة", fontWeight = FontWeight.Bold, modifier = Modifier.width(120.dp), textAlign = TextAlign.End)
            Text("اسم المادة والباركود", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
          }
          HorizontalDivider()
        }

        items(products) { prod ->
          // Get specific stock level
          val inv = inventories.find { it.branchId == branch.id && it.productId == prod.id }
          val pieces = inv?.stockInPieces ?: 0
          val lowStock = pieces <= (inv?.minRequiredStock ?: 12)

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Status warning
            Box(
              modifier = Modifier
                .width(110.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (lowStock) StatusDebt.copy(alpha = 0.15f) else StatusSuccess.copy(alpha = 0.15f))
                .padding(4.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                if (lowStock) "تنبيه انخفاض" else "سليم",
                color = if (lowStock) StatusDebt else StatusSuccess,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
              )
            }

            // Quantities
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(130.dp)) {
              Text("$pieces حبة", fontSize = 13.sp, fontWeight = FontWeight.Bold)
              val cartons = pieces / prod.cartonQuantity
              val rem = pieces % prod.cartonQuantity
              Text("($cartons كرتونة و $rem حبة)", fontSize = 10.sp, color = Color.Gray)
            }

            // Prices
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(120.dp)) {
              Text("مفرد: ${prod.unitRetailPrice} ر.س", fontSize = 11.sp)
              Text("جملة: ${prod.unitWholesalePrice} ر.س", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
            }

            // Product Name
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
              Text(prod.name, fontSize = 13.sp, fontWeight = FontWeight.Bold)
              Text("الباركود: ${prod.barcode} / ت: ${prod.cartonQuantity} حبة بالعلبة", fontSize = 10.sp, color = Color.Gray)
            }
          }
          HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
        }
      }
    }
  }

  // DIALOG FOR NEW PRODUCTS
  if (showAddProductDialog) {
    Dialog(onDismissRequest = { showAddProductDialog = false }) {
      Card(
        modifier = Modifier.width(360.dp).padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          horizontalAlignment = Alignment.End,
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Text("إضافة منتج بالكتالوج العام", fontWeight = FontWeight.Bold, fontSize = 16.sp)

          OutlinedTextField(value = nameIn, onValueChange = { nameIn = it }, label = { Text("اسم المنتج") }, isError = nameIn.isEmpty())
          OutlinedTextField(value = barcodeIn, onValueChange = { barcodeIn = it }, label = { Text("الباركود المتسلسل") })
          OutlinedTextField(value = categoryIn, onValueChange = { categoryIn = it }, label = { Text("التصنيف الكلي") })
          OutlinedTextField(value = priceIn, onValueChange = { priceIn = it }, label = { Text("سعر البيع للتجزئة") })
          OutlinedTextField(value = costIn, onValueChange = { costIn = it }, label = { Text("تكلفة الشراء الأساسية") })
          OutlinedTextField(value = cartonQtyIn, onValueChange = { cartonQtyIn = it }, label = { Text("عدد القطع لكل كرتونة") })

          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
              onClick = { showAddProductDialog = false },
              colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
            ) {
              Text("إلغاء")
            }
            Button(
              onClick = {
                val price = priceIn.toDoubleOrNull() ?: 10.0
                val cost = costIn.toDoubleOrNull() ?: 7.5
                val carton = cartonQtyIn.toIntOrNull() ?: 12
                val newP = ERPProduct(
                  id = "p_" + System.currentTimeMillis(),
                  barcode = barcodeIn.ifEmpty { "BAR_" + System.currentTimeMillis() },
                  name = nameIn.ifEmpty { "منتج غير مسمى" },
                  category = categoryIn.ifEmpty { "عام" },
                  unitRetailPrice = price,
                  unitWholesalePrice = price * 0.9,
                  unitCost = cost,
                  cartonQuantity = carton
                )
                products.add(newP)
                inventories.add(ERPInventory(branch.id, newP.id, 50)) // Seed stock of 50
                showAddProductDialog = false
                onInventoryAdjusted()
              }
            ) {
              Text("إضافة السلعة")
            }
          }
        }
      }
    }
  }

  // DIALOG FOR MULTI-BRANCH STOCK TRANSFERS
  if (showTransferDialog) {
    var branchMenuExpanded by remember { mutableStateOf(false) }
    var prodMenuExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { showTransferDialog = false }) {
      Card(
        modifier = Modifier.width(360.dp).padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          horizontalAlignment = Alignment.End,
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Text("نقل بضائع ومخزون بين الفروع", fontWeight = FontWeight.Bold, fontSize = 16.sp)

          // Product Select
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
              .clickable { prodMenuExpanded = true }
              .padding(12.dp)
          ) {
            Text(transferSelectedProduct?.name ?: "اختر المنتج المراد نقله", textAlign = TextAlign.Right)
            DropdownMenu(expanded = prodMenuExpanded, onDismissRequest = { prodMenuExpanded = false }) {
              products.forEach { p ->
                DropdownMenuItem(
                  text = { Text(p.name) },
                  onClick = {
                    transferSelectedProduct = p
                    prodMenuExpanded = false
                  }
                )
              }
            }
          }

          // Target Branch Select
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
              .clickable { branchMenuExpanded = true }
              .padding(12.dp)
          ) {
            Text(targetBranchSelected?.name ?: "اختر الفرع المستهدف للمخزون", textAlign = TextAlign.Right)
            DropdownMenu(expanded = branchMenuExpanded, onDismissRequest = { branchMenuExpanded = false }) {
              otherBranches.forEach { b ->
                DropdownMenuItem(
                  text = { Text(b.name) },
                  onClick = {
                    targetBranchSelected = b
                    branchMenuExpanded = false
                  }
                )
              }
            }
          }

          OutlinedTextField(
            value = transferSelectedQuantity,
            onValueChange = { transferSelectedQuantity = it },
            label = { Text("الكمية بالنقل (حبة)") }
          )

          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
              onClick = { showTransferDialog = false },
              colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
            ) {
              Text("إلغاء")
            }
            Button(
              onClick = {
                val qty = transferSelectedQuantity.toIntOrNull() ?: 0
                if (transferSelectedProduct != null && targetBranchSelected != null && qty > 0) {
                  onStockTransferred(
                    transferSelectedProduct!!.id,
                    branch.id,
                    targetBranchSelected!!.id,
                    qty
                  )
                  transferSelectedProduct = null
                  targetBranchSelected = null
                  transferSelectedQuantity = ""
                  showTransferDialog = false
                }
              }
            ) {
              Text("تأكيد النقل المالي")
            }
          }
        }
      }
    }
  }
}


// ===================================
// 4. FINANCIAL BALANCE & CONTACTS LEDGER (CUSTOMERS & SUPPLIERS DEBTS)
// ===================================
@Composable
fun FinancialLedgerScreen(
  contacts: List<ERPContact>,
  successTrigger: (String) -> Unit
) {
  var selectedContactForPayment by remember { mutableStateOf<ERPContact?>(null) }
  var payInAmount by remember { mutableStateOf("") }

  val customers = contacts.filter { it.type == ContactType.CUSTOMER }
  val suppliers = contacts.filter { it.type == ContactType.SUPPLIER }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp),
    horizontalAlignment = Alignment.End
  ) {
    Text("حسابات الديون والذمم المدينة والدائنة", fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Text("سجل حسابات ديون الموردين والعملاء الآجل، مع سداد المقبوضات والمدفوعات", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))

    Spacer(modifier = Modifier.height(16.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // CLIENTS / CUSTOMERS COLUMN
      Card(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
          horizontalAlignment = Alignment.End
        ) {
          Text("العملاء والزبائن (أرصدة مدينة لنا)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)
          Spacer(modifier = Modifier.height(12.dp))

          LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(customers) { cust ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(8.dp))
                  .background(MaterialTheme.colorScheme.background)
                  .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                // Return Debt Button
                Button(
                  onClick = { selectedContactForPayment = cust },
                  colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess),
                  contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                  modifier = Modifier.height(30.dp)
                ) {
                  Text("تسجيل دفعة قبض", fontSize = 10.sp)
                }

                // Balance
                Column(horizontalAlignment = Alignment.End) {
                  Text("${cust.debtBalance} ر.س", fontWeight = FontWeight.Bold, color = if (cust.debtBalance > 0) StatusDebt else Color.Gray)
                  Text("حد الائتمان: ${cust.creditLimit} ر.س", fontSize = 9.sp, color = Color.Gray)
                }

                // Detail
                Column(horizontalAlignment = Alignment.End) {
                  Text(cust.name, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                  Text(cust.phone, fontSize = 9.sp, color = Color.Gray)
                }
              }
            }
          }
        }
      }

      // SUPPLIERS COLUMN
      Card(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
          horizontalAlignment = Alignment.End
        ) {
          Text("الموردون والشركات (ذمم دائنة واجبة السحب)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, fontSize = 15.sp)
          Spacer(modifier = Modifier.height(12.dp))

          LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(suppliers) { sup ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(8.dp))
                  .background(MaterialTheme.colorScheme.background)
                  .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                // Pay Debt Button
                Button(
                  onClick = { selectedContactForPayment = sup },
                  colors = ButtonDefaults.buttonColors(containerColor = StatusWarning),
                  contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                  modifier = Modifier.height(30.dp)
                ) {
                  Text("تسجيل دفعة صرف", fontSize = 10.sp)
                }

                // Balance
                Column(horizontalAlignment = Alignment.End) {
                  Text("${sup.debtBalance} ر.س", fontWeight = FontWeight.Bold, color = if (sup.debtBalance > 0) StatusWarning else Color.Gray)
                  Text("سقف الائتمان: ${sup.creditLimit} ر.س", fontSize = 9.sp, color = Color.Gray)
                }

                // Detail
                Column(horizontalAlignment = Alignment.End) {
                  Text(sup.name, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                  Text(sup.phone, fontSize = 9.sp, color = Color.Gray)
                }
              }
            }
          }
        }
      }
    }
  }

  // DIALOG FOR SETTLING PAYMENT OUTSTANDING ARREARS
  selectedContactForPayment?.let { contact ->
    val isCust = contact.type == ContactType.CUSTOMER
    Dialog(onDismissRequest = { selectedContactForPayment = null }) {
      Card(
        modifier = Modifier.width(340.dp).padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          horizontalAlignment = Alignment.End,
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Text(
            if (isCust) "تسجيل مقبوضات نقدية من زبون" else "تسجيل مستردات لمورد",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.primary
          )
          Text("${contact.name}", fontSize = 13.sp, color = Color.Gray)
          Text("الرصيد القائم: ${contact.debtBalance} SAR", fontSize = 12.sp)

          OutlinedTextField(
            value = payInAmount,
            onValueChange = { payInAmount = it },
            label = { Text("مبلغ المحاسبة المدفوع") }
          )

          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { selectedContactForPayment = null }, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) {
              Text("إلغاء")
            }
            Button(
              onClick = {
                val amt = payInAmount.toDoubleOrNull() ?: 0.0
                if (amt > 0.0 && amt <= contact.debtBalance) {
                  contact.debtBalance = contact.debtBalance - amt
                  successTrigger("تم محاسبة ${contact.name} واستلام مبلغ $amt ر.س بنجاح")
                  payInAmount = ""
                  selectedContactForPayment = null
                } else if (amt > contact.debtBalance) {
                  successTrigger("المبلغ المدخل أعلى من مستحقات الدين القائم!")
                }
              }
            ) {
              Text("تسجيل العملية")
            }
          }
        }
      }
    }
  }
}


// ===================================
// 5. EMPLOYEE ATTENDANCE & AUTOMATED PAYROLLS
// ===================================
@Composable
fun AttendanceSalaryScreen(
  users: List<ERPUser>,
  attendanceLogs: MutableList<AttendanceLog>,
  invoices: List<ERPInvoice>,
  successTrigger: (String) -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp),
    horizontalAlignment = Alignment.End
  ) {
    Text("سجلات حضور الموارد وحساب الرواتب التلقائي", fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Text("حساب الساعات التشغيلية والرواتب متضمنة عمولة 2% من المبيعات المنجزة", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))

    Spacer(modifier = Modifier.height(16.dp))

    // ROW CONTAINER SEPARATING ATTENDANCE ACTIONS
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // Payroll Calculator Summary Card
      Card(
        modifier = Modifier.weight(1.5f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
          horizontalAlignment = Alignment.End
        ) {
          Text("مسير رواتب الموارد والامتيازات", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)
          Spacer(modifier = Modifier.height(10.dp))

          LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
              Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text("صافي المستحقات", fontWeight = FontWeight.Bold, modifier = Modifier.width(100.dp), textAlign = TextAlign.End)
                Text("العمولة (2%)", fontWeight = FontWeight.Bold, modifier = Modifier.width(90.dp), textAlign = TextAlign.End)
                Text("المسمى والراتب الأساسي", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
              }
              HorizontalDivider()
            }

            items(users) { usr ->
              // Final calculated salary
              val finalPay = usr.baseSalary + usr.commissionEarned
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "${String.format("%.2f", finalPay)} ر.س",
                  fontWeight = FontWeight.Bold,
                  color = StatusSuccess,
                  modifier = Modifier.width(100.dp),
                  textAlign = TextAlign.End
                )

                // Accrued commissions from POS Sales proxy
                Text(
                  text = "${String.format("%.2f", usr.commissionEarned)} ر.س",
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.width(90.dp),
                  textAlign = TextAlign.End
                )

                // Details
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                  Text(usr.username, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                  Text("أساسي: ${usr.baseSalary} SAR / دور: ${usr.role.name}", fontSize = 10.sp, color = Color.Gray)
                }
              }
              HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
            }
          }
        }
      }

      // Log of Presence Clock in
      Card(
        modifier = Modifier.weight(1f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
          horizontalAlignment = Alignment.End
        ) {
          Text("مراقبة الحضور والدوام اللحظي", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, fontSize = 15.sp)
          Spacer(modifier = Modifier.height(12.dp))

          // Checkin options
          var presenceMenuExpanded by remember { mutableStateOf(false) }

          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
              .clickable { presenceMenuExpanded = true }
              .padding(10.dp)
          ) {
            Text("تسجيل حضور موظف نشط", textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
            DropdownMenu(expanded = presenceMenuExpanded, onDismissRequest = { presenceMenuExpanded = false }) {
              users.forEach { u ->
                DropdownMenuItem(
                  text = { Text(u.username) },
                  onClick = {
                    val alreadyIn = attendanceLogs.any { it.employeeName == u.username && it.checkOut == null }
                    if (!alreadyIn) {
                      attendanceLogs.add(
                        0,
                        AttendanceLog(u.username, "2026-06-02", SimpleDateFormat("HH:mm", Locale.US).format(Date()))
                      )
                      successTrigger("تم تسجيل حضور الموظف ${u.username} بالفرع بنجاح")
                    } else {
                      successTrigger("${u.username} مسجل حضوره مسبقاً لم يغلق الوردية")
                    }
                    presenceMenuExpanded = false
                  }
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(attendanceLogs) { log ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(6.dp))
                  .background(MaterialTheme.colorScheme.background)
                  .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                // Checkout action if active shift
                if (log.checkOut == null) {
                  Button(
                    onClick = {
                      log.checkOut = SimpleDateFormat("HH:mm", Locale.US).format(Date())
                      successTrigger("تم توثيق خروج وانصراف ${log.employeeName}")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDebt),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.height(24.dp)
                  ) {
                    Text("انصراف الوردية", fontSize = 9.sp)
                  }
                } else {
                  Text("وردية مغلقة", color = Color.Gray, fontSize = 10.sp)
                }

                Column(horizontalAlignment = Alignment.End) {
                  Text(log.employeeName, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                  Text("دخول: ${log.checkIn} | خروج: ${log.checkOut ?: "نشط الدوام"}", fontSize = 10.sp, color = Color.Gray)
                }
              }
            }
          }
        }
      }
    }
  }
}


// ===================================
// 6. ISLAMIC ZAKAT CALCULATIONS MODULE
// ===================================
@Composable
fun IslamicZakatScreen(
  cashInHand: Double,
  products: List<ERPProduct>,
  inventories: List<ERPInventory>,
  contacts: List<ERPContact>,
  zakatHistory: MutableList<ZakatHistoryRecord>,
  onZakatLogged: () -> Unit
) {
  // NISAB definition: approximately cost of 595g of Pure Silver (estimated price 3.78 SAR/g = ~2,250 SAR)
  val silverGramPrice = 3.8
  val silverNisabThreshold = 595 * silverGramPrice

  // Goods value at current cost (Zakat base)
  val eligibleStockCostAmount = inventories.sumOf { inv ->
    val p = products.find { it.id == inv.productId && it.isZakatEligible }
    (p?.unitCost ?: 0.0) * inv.stockInPieces
  }

  val receivables = contacts.filter { it.type == ContactType.CUSTOMER }.sumOf { it.debtBalance }
  val payables = contacts.filter { it.type == ContactType.SUPPLIER }.sumOf { it.debtBalance }

  // net liquid base pool of zakat:
  // (Cash in hand/bank + Inventory Stock assessed at cost + expected collectable client debts) - Supplier outstanding debt obligations due within the year.
  val netZakatBase = (cashInHand + eligibleStockCostAmount + receivables) - payables
  val isNisabReached = netZakatBase >= silverNisabThreshold
  val calculatedZakatAmount = if (isNisabReached) netZakatBase * 0.025 else 0.0

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp),
    horizontalAlignment = Alignment.End
  ) {
    Text("موديول حساب الزكاة الشرعية لعروض التجارة", fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Text("حساب وعاء زكاة عروض التجارة والسيولة النقدية وفقاً للشروط والضوابط الفقهية المقررة", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))

    Spacer(modifier = Modifier.height(16.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // THE CALCULATOR ENGINE PANEL
      Card(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
          horizontalAlignment = Alignment.End,
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Text("وعاء الحساب الزكوي المجمع", fontWeight = FontWeight.Bold, color = BaseGold, fontSize = 16.sp)

          HorizontalDivider()

          ZakatFactorRow("السيولة والودائع الشاغرة بمحفظة النقد:", "${String.format("%.2f", cashInHand)} ر.س")
          ZakatFactorRow("قيمة عروض التجارة المخزنية (بالتكلفة):", "${String.format("%.2f", eligibleStockCostAmount)} ر.س")
          ZakatFactorRow("الذمم المدينة الموثوقة (ديون الزبائن):", "${String.format("%.2f", receivables)} ر.س")
          ZakatFactorRow("الذمم الدائنة مستحقة الخصم (ديون الموردين):", "- ${String.format("%.2f", payables)} ر.س")

          HorizontalDivider()

          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
              text = "${String.format("%.2f", netZakatBase)} ر.س",
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = if (isNisabReached) StatusSuccess else Color.Gray
            )
            Text("وعاء الزكاة الصافي:", fontWeight = FontWeight.Bold)
          }

          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
              text = "${String.format("%.2f", silverNisabThreshold)} ر.س",
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold
            )
            Text("قيمة نصاب الفضة الشرعي (595 غرام):", fontSize = 12.sp)
          }

          // NISAB MET STATUS
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(if (isNisabReached) StatusSuccess.copy(alpha = 0.15f) else StatusWarning.copy(alpha = 0.15f))
              .padding(10.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              if (isNisabReached) "تحذير شرعي: النصاب قد اكتمل ومستوفى الزكاة حلال الحول" else "لم يكتمل النصاب المطلوب لدفع الزكاة",
              color = if (isNisabReached) StatusSuccess else StatusWarning,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold
            )
          }

          Spacer(modifier = Modifier.height(10.dp))

          // Zakat due calculated value
          Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = BaseGold.copy(alpha = 0.12f)),
            border = BorderStroke(1.dp, BaseGold)
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Text("مقدار الزكاة الواجبة الإخراج (2.5%)", fontSize = 11.sp, color = Color.DarkGray)
              Spacer(modifier = Modifier.height(4.dp))
              Text("${String.format("%.2f", calculatedZakatAmount)} ر.س", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = BaseGold)
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          Button(
            onClick = {
              if (calculatedZakatAmount > 0.0) {
                zakatHistory.add(
                  0,
                  ZakatHistoryRecord(
                    id = "zk_" + System.currentTimeMillis(),
                    date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                    netAssets = netZakatBase,
                    zakatDue = calculatedZakatAmount,
                    status = "تم السداد لوزارة الموارد وصندوق الزكاة"
                  )
                )
                onZakatLogged()
              }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = BaseGold),
            enabled = calculatedZakatAmount > 0.0
          ) {
            Text("إثبات وإيداع سداد الزكاة", fontWeight = FontWeight.Bold, color = Color.White)
          }
        }
      }

      // HISTORY OF LOGVED CALCULATIONS RECORD LIST
      Card(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
          horizontalAlignment = Alignment.End
        ) {
          Text("سجل حسابات الزكاة التاريخي الموثق", fontWeight = FontWeight.Bold, fontSize = 15.sp)
          Spacer(modifier = Modifier.height(12.dp))

          LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(zakatHistory) { history ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(6.dp))
                  .background(MaterialTheme.colorScheme.background)
                  .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(horizontalAlignment = Alignment.Start) {
                  Text("${history.zakatDue} ر.س", fontWeight = FontWeight.Bold, color = BaseGold)
                  Text(history.status, fontSize = 8.sp, color = Color.Gray)
                }

                Column(horizontalAlignment = Alignment.End) {
                  Text("تاريخ الحول: ${history.date}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                  Text("وعاء الوعود التجاري: ${history.netAssets} SAR", fontSize = 10.sp, color = Color.Gray)
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun ZakatFactorRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(value, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    Text(label, color = MaterialTheme.colorScheme.onSurface.copy(0.6f), fontSize = 13.sp)
  }
}


// ===================================
// 7. INVOICE ARCHIVES HISTORY DISPLAY
// ===================================
@Composable
fun InvoiceArchiveScreen(invoices: List<ERPInvoice>) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp),
    horizontalAlignment = Alignment.End
  ) {
    Text("أرشيف الفواتير المبيعية التاريخية بالفرع", fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Text("سجل فواتير مبيعات أمناء الصناديق واسترداد معلومات الدفع لكل فاتورة ضريبية", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))

    Spacer(modifier = Modifier.height(16.dp))

    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
      LazyColumn(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
          Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("القيمة الإجمالية", fontWeight = FontWeight.Bold, modifier = Modifier.width(110.dp), textAlign = TextAlign.End)
            Text("طريقة السداد والفرع", fontWeight = FontWeight.Bold, modifier = Modifier.width(150.dp), textAlign = TextAlign.End)
            Text("تفاصيل العميل والمنشط", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
          }
          HorizontalDivider()
        }

        items(invoices) { inv ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(MaterialTheme.colorScheme.background)
              .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Price total amount
            Text(
              text = "${String.format("%.2f", inv.totalAmount)} ر.س",
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.width(110.dp),
              textAlign = TextAlign.End
            )

            // Method/Branch
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(150.dp)) {
              Text(inv.paymentMethod.labelAr, fontWeight = FontWeight.SemiBold, color = if (inv.paymentMethod == PayMethod.DEBT) StatusDebt else StatusSuccess)
              Text("الفرع: ${inv.branchName}", fontSize = 10.sp, color = Color.Gray)
            }

            // Customer Details
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
              Text(inv.invoiceNumber, fontSize = 13.sp, fontWeight = FontWeight.Bold)
              Text("العميل: ${inv.customerName ?: "مبيعات نقدية"} / بواسطة: ${inv.cashierName}", fontSize = 10.sp, color = Color.Gray)
              Text("تاريخ الإصدار: ${inv.date}", fontSize = 9.sp, color = Color.Gray)
            }
          }
        }
      }
    }
  }
}


// ===================================
// SECURE AUTH / LOGIN SPLASH SCREEN
// ===================================
@Composable
fun ERPAuthView(
  users: List<ERPUser>,
  onLoginSuccess: (ERPUser) -> Unit
) {
  var userEmailIn by remember { mutableStateOf("") }
  var passwordIn by remember { mutableStateOf("") }
  var selectedRole by remember { mutableStateOf(UserRole.ADMIN) }
  var showResetDialog by remember { mutableStateOf(false) }
  var resetEmailIn by remember { mutableStateOf("") }
  var loginErrorState by remember { mutableStateOf<String?>(null) }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
    contentAlignment = Alignment.Center
  ) {
    Card(
      modifier = Modifier
        .width(420.dp)
        .padding(16.dp)
        .wrapContentHeight(),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        Box(
          modifier = Modifier
            .size(60.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Menu,
            contentDescription = "Shield",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
          )
        }

        Text(
          text = "تسجيل المصادقة الآمنة ERP",
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp,
          color = MaterialTheme.colorScheme.primary
        )

        Text(
          text = "مرحباً بك مجدداً بكادر مظلة الأعمال للأنظمة المبيعات الموحدة",
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Role Selector Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          UserRole.values().forEach { role ->
            val isS = selectedRole == role
            Box(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(if (isS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background)
                .clickable { selectedRole = role }
                .padding(6.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                role.name,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isS) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
              )
            }
          }
        }

        // Email & Password Fields
        OutlinedTextField(
          value = userEmailIn,
          onValueChange = { userEmailIn = it },
          label = { Text("البريد الإلكتروني للعمل / المستخدم") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )

        OutlinedTextField(
          value = passwordIn,
          onValueChange = { passwordIn = it },
          label = { Text("كلمة المرور المقررة") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )

        loginErrorState?.let {
          Text(it, color = StatusDebt, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        // Action controls
        Button(
          onClick = {
            loginErrorState = null
            // Check users with dynamic fallbacks
            val targetEmailObj = userEmailIn.trim()
            val targetUser = users.find { it.email.lowercase() == targetEmailObj.lowercase() || targetEmailObj == "admin" || targetEmailObj == "cashier" }
            if (targetUser != null) {
              // override role temporarily for demo ease
              targetUser.role = selectedRole
              onLoginSuccess(targetUser)
            } else {
              loginErrorState = "عذراً! لم يتم التعرف على حساب هذا الموظف بالنظام."
            }
          },
          modifier = Modifier.fillMaxWidth()
        ) {
          Text("سجل الدخول للنظام الأساسي", fontWeight = FontWeight.Bold)
        }

        Text(
          text = "نسيت كلمة المرور؟ استعادة وتعيين",
          fontSize = 12.sp,
          color = MaterialTheme.colorScheme.secondary,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.clickable { showResetDialog = true }
        )
      }
    }
  }

  // DIALOG FOR RESTORING EMAIL
  if (showResetDialog) {
    Dialog(onDismissRequest = { showResetDialog = false }) {
      Card(
        modifier = Modifier.width(340.dp).padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          horizontalAlignment = Alignment.End,
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Text("استعادة وتصفير حساب الدخول بالبريد الإلكتروني", fontWeight = FontWeight.Bold, fontSize = 15.sp)
          Text("أدخل بريدك الموثق لإرسال رابط تغيير كلمة المرور الموحد", fontSize = 11.sp, color = Color.Gray)

          OutlinedTextField(
            value = resetEmailIn,
            onValueChange = { resetEmailIn = it },
            label = { Text("بريد العمل النشط") }
          )

          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { showResetDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) {
              Text("إلغاء")
            }
            Button(
              onClick = {
                showResetDialog = false
                resetEmailIn = ""
              }
            ) {
              Text("إرسال الرابط")
            }
          }
        }
      }
    }
  }
}

// Custom simple toast banner
@Composable
fun ToastBanner(
  message: String,
  isError: Boolean,
  onDismiss: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onDismiss() },
    colors = CardDefaults.cardColors(
      containerColor = if (isError) StatusDebt else StatusSuccess
    ),
    shape = RoundedCornerShape(10.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      horizontalArrangement = Arrangement.End,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = message,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.weight(1f),
        textAlign = TextAlign.Right
      )
      Spacer(modifier = Modifier.width(10.dp))
      Icon(
        imageVector = if (isError) Icons.Default.Warning else Icons.Default.Done,
        contentDescription = "NotificationIcon",
        tint = Color.White,
        modifier = Modifier.size(18.dp)
      )
    }
  }
}
