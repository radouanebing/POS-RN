/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 * 
 * ERP & POS Backend Sequelize ORM Structural Schema
 * Language: TypeScript
 * ORM: Sequelize
 */

import { Sequelize, DataTypes, Model, Optional } from 'sequelize';

// Initialize mock or development Sequelize instance for structure demonstration
export const sequelize = new Sequelize({
  dialect: 'sqlite',
  storage: ':memory:',
  logging: false,
});

// ==========================================
// 1. MODULE: AUTH, EMPLOYEES & SALARIES
// ==========================================

export enum UserRole {
  ADMIN = 'admin',
  BRANCH_MANAGER = 'branch_manager',
  CASHIER = 'cashier'
}

interface UserAttributes {
  id: string;
  username: string;
  email: string;
  passwordHash: string;
  role: UserRole;
  isActive: boolean;
  branchId?: string | null;
  baseSalary: number;
  hourlyRate: number;
  performanceBonusRate: number; // Percentage on sales generated
}

interface UserCreationAttributes extends Optional<UserAttributes, 'id' | 'branchId' | 'isActive'> {}

export class User extends Model<UserAttributes, UserCreationAttributes> implements UserAttributes {
  public id!: string;
  public username!: string;
  public email!: string;
  public passwordHash!: string;
  public role!: UserRole;
  public isActive!: boolean;
  public branchId!: string | null;
  public baseSalary!: number;
  public hourlyRate!: number;
  public performanceBonusRate!: number;
}

User.init(
  {
    id: {
      type: DataTypes.UUID,
      defaultValue: DataTypes.UUIDV4,
      primaryKey: true,
    },
    username: {
      type: DataTypes.STRING(50),
      allowNull: false,
      unique: true,
    },
    email: {
      type: DataTypes.STRING(100),
      allowNull: false,
      unique: true,
      validate: { isEmail: true },
    },
    passwordHash: {
      type: DataTypes.STRING(255),
      allowNull: false,
    },
    role: {
      type: DataTypes.ENUM(...Object.values(UserRole)),
      defaultValue: UserRole.CASHIER,
    },
    isActive: {
      type: DataTypes.BOOLEAN,
      defaultValue: true,
    },
    branchId: {
      type: DataTypes.UUID,
      allowNull: true,
    },
    baseSalary: {
      type: DataTypes.DECIMAL(10, 2),
      defaultValue: 0.0,
    },
    hourlyRate: {
      type: DataTypes.DECIMAL(10, 2),
      defaultValue: 0.0,
    },
    performanceBonusRate: {
      type: DataTypes.DECIMAL(5, 2),
      defaultValue: 0.0, // e.g., 2% bonus on POS sales
    },
  },
  { sequelize, tableName: 'users' }
);


// MODULE: EMPLOYEE ATTENDANCE
interface AttendanceAttributes {
  id: string;
  userId: string;
  checkIn: Date;
  checkOut?: Date | null;
  date: Date;
  overtimeHours: number;
  isAbsent: boolean;
}

export class Attendance extends Model<AttendanceAttributes> implements AttendanceAttributes {
  public id!: string;
  public userId!: string;
  public checkIn!: Date;
  public checkOut!: Date | null;
  public date!: Date;
  public overtimeHours!: number;
  public isAbsent!: boolean;
}

Attendance.init(
  {
    id: {
      type: DataTypes.UUID,
      defaultValue: DataTypes.UUIDV4,
      primaryKey: true,
    },
    userId: {
      type: DataTypes.UUID,
      allowNull: false,
    },
    checkIn: {
      type: DataTypes.DATE,
      allowNull: false,
    },
    checkOut: {
      type: DataTypes.DATE,
      allowNull: true,
    },
    date: {
      type: DataTypes.DATEONLY,
      allowNull: false,
    },
    overtimeHours: {
      type: DataTypes.DECIMAL(4, 2),
      defaultValue: 0.0,
    },
    isAbsent: {
      type: DataTypes.BOOLEAN,
      defaultValue: false,
    },
  },
  { sequelize, tableName: 'attendance' }
);


// ==========================================
// 2. MODULE: BRANCHES & PRODUCTS INVENTORY
// ==========================================

interface BranchAttributes {
  id: string;
  name: string;
  location: string;
  phone: string;
}

export class Branch extends Model<BranchAttributes> implements BranchAttributes {
  public id!: string;
  public name!: string;
  public location!: string;
  public phone!: string;
}

Branch.init(
  {
    id: {
      type: DataTypes.UUID,
      defaultValue: DataTypes.UUIDV4,
      primaryKey: true,
    },
    name: {
      type: DataTypes.STRING(100),
      allowNull: false,
      unique: true,
    },
    location: {
      type: DataTypes.STRING(255),
      allowNull: false,
    },
    phone: {
      type: DataTypes.STRING(20),
      allowNull: false,
    },
  },
  { sequelize, tableName: 'branches' }
);


interface ProductAttributes {
  id: string;
  barcode: string;
  name: string;
  description?: string;
  category: string;
  unitRetailPrice: number;    // Single piece retail price
  unitWholesalePrice: number; // Single piece wholesale price (when bought in bulk/carton)
  unitCost: number;           // Buying cost
  cartonQuantity: number;     // How many pieces in a carton (e.g., 12 or 24)
  isZakatEligible: boolean;   // Inventory subject to Zakat calculation
}

export class Product extends Model<ProductAttributes> implements ProductAttributes {
  public id!: string;
  public barcode!: string;
  public name!: string;
  public description!: string;
  public category!: string;
  public unitRetailPrice!: number;
  public unitWholesalePrice!: number;
  public unitCost!: number;
  public cartonQuantity!: number;
  public isZakatEligible!: boolean;
}

Product.init(
  {
    id: {
      type: DataTypes.UUID,
      defaultValue: DataTypes.UUIDV4,
      primaryKey: true,
    },
    barcode: {
      type: DataTypes.STRING(50),
      allowNull: false,
      unique: true,
    },
    name: {
      type: DataTypes.STRING(255),
      allowNull: false,
    },
    description: {
      type: DataTypes.TEXT,
      allowNull: true,
    },
    category: {
      type: DataTypes.STRING(100),
      allowNull: false,
    },
    unitRetailPrice: {
      type: DataTypes.DECIMAL(12, 2),
      allowNull: false,
    },
    unitWholesalePrice: {
      type: DataTypes.DECIMAL(12, 2),
      allowNull: false,
    },
    unitCost: {
      type: DataTypes.DECIMAL(12, 2),
      allowNull: false,
    },
    cartonQuantity: {
      type: DataTypes.INTEGER,
      defaultValue: 1, // Default to 1 piece if no carton division
    },
    isZakatEligible: {
      type: DataTypes.BOOLEAN,
      defaultValue: true,
    },
  },
  { sequelize, tableName: 'products' }
);


// JUNCTION TABLE FOR MULTI-BRANCH STOCK LEVEL
interface InventoryAttributes {
  id: string;
  branchId: string;
  productId: string;
  stockInPieces: number; // Stores all items in basic unit (pieces)
  minimumRequiredStock: number;
}

export class Inventory extends Model<InventoryAttributes> implements InventoryAttributes {
  public id!: string;
  public branchId!: string;
  public productId!: string;
  public stockInPieces!: number;
  public minimumRequiredStock!: number;
}

Inventory.init(
  {
    id: {
      type: DataTypes.UUID,
      defaultValue: DataTypes.UUIDV4,
      primaryKey: true,
    },
    branchId: {
      type: DataTypes.UUID,
      allowNull: false,
    },
    productId: {
      type: DataTypes.UUID,
      allowNull: false,
    },
    stockInPieces: {
      type: DataTypes.INTEGER,
      defaultValue: 0,
    },
    minimumRequiredStock: {
      type: DataTypes.INTEGER,
      defaultValue: 10,
    },
  },
  {
    sequelize,
    tableName: 'inventories',
    indexes: [{ unique: true, fields: ['branchId', 'productId'] }],
  }
);


// ==========================================
// 3. MODULE: CUSTOMERS & SUPPLIERS DEBTS
// ==========================================

interface ContactAttributes {
  id: string;
  name: string;
  phone: string;
  email?: string;
  address?: string;
  type: 'customer' | 'supplier';
  debtBalance: number; // Positive = we owe them (for supplier) or they owe us (for customer)
  creditLimit: number; // Maximum negative/debt ceiling allowed
}

export class Contact extends Model<ContactAttributes> implements ContactAttributes {
  public id!: string;
  public name!: string;
  public phone!: string;
  public email!: string;
  public address!: string;
  public type!: 'customer' | 'supplier';
  public debtBalance!: number;
  public creditLimit!: number;
}

Contact.init(
  {
    id: {
      type: DataTypes.UUID,
      defaultValue: DataTypes.UUIDV4,
      primaryKey: true,
    },
    name: {
      type: DataTypes.STRING(150),
      allowNull: false,
    },
    phone: {
      type: DataTypes.STRING(20),
      allowNull: false,
    },
    email: {
      type: DataTypes.STRING(100),
      allowNull: true,
    },
    address: {
      type: DataTypes.TEXT,
      allowNull: true,
    },
    type: {
      type: DataTypes.ENUM('customer', 'supplier'),
      allowNull: false,
    },
    debtBalance: {
      type: DataTypes.DECIMAL(12, 2),
      defaultValue: 0.0,
    },
    creditLimit: {
      type: DataTypes.DECIMAL(12, 2),
      defaultValue: 5000.0, // Default debt limit
    },
  },
  { sequelize, tableName: 'contacts' }
);


// ==========================================
// 4. MODULE: SALES, PURCHASES & POS TRANSACTION DETAILS
// ==========================================

export enum TransactionType {
  SALE = 'sale',
  PURCHASE = 'purchase',
  STOCK_TRANSFER = 'stock_transfer'
}

export enum PaymentMethod {
  CASH = 'cash',
  CARD = 'card',
  DEBT = 'debt', // On credit
  MIXED = 'mixed'
}

interface TransactionAttributes {
  id: string;
  invoiceNumber: string;
  type: TransactionType;
  branchId: string;
  targetBranchId?: string | null; // For stock transfers
  cashierId: string;
  contactId?: string | null;  // Customer or Supplier details
  paymentMethod: PaymentMethod;
  totalAmount: number;
  totalCost: number;          // Accumulated buying cost to measure raw profits
  paidAmount: number;
  debtAmount: number;
  transactionDate: Date;
}

export class Transaction extends Model<TransactionAttributes> implements TransactionAttributes {
  public id!: string;
  public invoiceNumber!: string;
  public type!: TransactionType;
  public branchId!: string;
  public targetBranchId!: string | null;
  public cashierId!: string;
  public contactId!: string | null;
  public paymentMethod!: PaymentMethod;
  public totalAmount!: number;
  public totalCost!: number;
  public paidAmount!: number;
  public debtAmount!: number;
  public transactionDate!: Date;
}

Transaction.init(
  {
    id: {
      type: DataTypes.UUID,
      defaultValue: DataTypes.UUIDV4,
      primaryKey: true,
    },
    invoiceNumber: {
      type: DataTypes.STRING(50),
      allowNull: false,
      unique: true,
    },
    type: {
      type: DataTypes.ENUM(...Object.values(TransactionType)),
      allowNull: false,
    },
    branchId: {
      type: DataTypes.UUID,
      allowNull: false,
    },
    targetBranchId: {
      type: DataTypes.UUID,
      allowNull: true,
    },
    cashierId: {
      type: DataTypes.UUID,
      allowNull: false,
    },
    contactId: {
      type: DataTypes.UUID,
      allowNull: true, // Optional customer / supplier
    },
    paymentMethod: {
      type: DataTypes.ENUM(...Object.values(PaymentMethod)),
      defaultValue: PaymentMethod.CASH,
    },
    totalAmount: {
      type: DataTypes.DECIMAL(12, 2),
      allowNull: false,
    },
    totalCost: {
      type: DataTypes.DECIMAL(12, 2),
      defaultValue: 0.0,
    },
    paidAmount: {
      type: DataTypes.DECIMAL(12, 2),
      defaultValue: 0.0,
    },
    debtAmount: {
      type: DataTypes.DECIMAL(12, 2),
      defaultValue: 0.0,
    },
    transactionDate: {
      type: DataTypes.DATE,
      defaultValue: DataTypes.NOW,
    },
  },
  {
    sequelize,
    tableName: 'transactions',
    hooks: {
      // Automatic debt adjustments for Customer/Supplier on invoices creation
      afterCreate: async (transactionInstance: Transaction) => {
        if (transactionInstance.debtAmount > 0 && transactionInstance.contactId) {
          const contact = await Contact.findByPk(transactionInstance.contactId);
          if (contact) {
            // For general sale, customer debt balance increases
            // For purchase, supplier debt we need to pay increases
            contact.debtBalance = Number(contact.debtBalance) + Number(transactionInstance.debtAmount);
            await contact.save();
          }
        }
      },
    },
  }
);


interface TransactionItemAttributes {
  id: string;
  transactionId: string;
  productId: string;
  quantity: number;        // Sold amount in "pieces"
  soldUnit: 'piece' | 'carton';
  unitPrice: number;       // Record exact pricing active at transaction time
  costAtTransaction: number;
  subtotal: number;
}

export class TransactionItem extends Model<TransactionItemAttributes> implements TransactionItemAttributes {
  public id!: string;
  public transactionId!: string;
  public productId!: string;
  public quantity!: number;
  public soldUnit!: 'piece' | 'carton';
  public unitPrice!: number;
  public costAtTransaction!: number;
  public subtotal!: number;
}

TransactionItem.init(
  {
    id: {
      type: DataTypes.UUID,
      defaultValue: DataTypes.UUIDV4,
      primaryKey: true,
    },
    transactionId: {
      type: DataTypes.UUID,
      allowNull: false,
    },
    productId: {
      type: DataTypes.UUID,
      allowNull: false,
    },
    quantity: {
      type: DataTypes.INTEGER,
      allowNull: false,
    },
    soldUnit: {
      type: DataTypes.ENUM('piece', 'carton'),
      defaultValue: 'piece',
    },
    unitPrice: {
      type: DataTypes.DECIMAL(12, 2),
      allowNull: false,
    },
    costAtTransaction: {
      type: DataTypes.DECIMAL(12, 2),
      allowNull: false,
    },
    subtotal: {
      type: DataTypes.DECIMAL(12, 2),
      allowNull: false,
    },
  },
  { sequelize, tableName: 'transaction_items' }
);


// ==========================================
// 5. MODULE: ISLAMIC COMMERCIAL ZAKAT LOGS
// ==========================================

interface ZakatCalculationAttributes {
  id: string;
  calculationDate: Date;
  cashInHand: number;          // Available cash vaults & bank accounts
  goodsValue: number;          // Cumulative buying cost of current Zakat-eligible products stock
  collectableReceivables: number; // Debts expected to be recovered from trusted clients
  deductiblePayables: number;     // Immediate liabilities owing to suppliers
  nisabValueSilverGrams: number;  // Price of 595g of silver at transaction date
  isNisabReached: boolean;
  zakatDue: number;            // 2.5% of net zakat base assets
}

export class ZakatCalculation extends Model<ZakatCalculationAttributes> implements ZakatCalculationAttributes {
  public id!: string;
  public calculationDate!: Date;
  public cashInHand!: number;
  public goodsValue!: number;
  public collectableReceivables!: number;
  public deductiblePayables!: number;
  public nisabValueSilverGrams!: number;
  public isNisabReached!: boolean;
  public zakatDue!: number;
}

ZakatCalculation.init(
  {
    id: {
      type: DataTypes.UUID,
      defaultValue: DataTypes.UUIDV4,
      primaryKey: true,
    },
    calculationDate: {
      type: DataTypes.DATE,
      defaultValue: DataTypes.NOW,
    },
    cashInHand: {
      type: DataTypes.DECIMAL(14, 2),
      allowNull: false,
    },
    goodsValue: {
      type: DataTypes.DECIMAL(14, 2),
      allowNull: false,
    },
    collectableReceivables: {
      type: DataTypes.DECIMAL(14, 2),
      defaultValue: 0.0,
    },
    deductiblePayables: {
      type: DataTypes.DECIMAL(14, 2),
      defaultValue: 0.0,
    },
    nisabValueSilverGrams: {
      type: DataTypes.DECIMAL(12, 2),
      defaultValue: 0.0, // Nisab in cash equivalent (approx cost of 595 grams of silver)
    },
    isNisabReached: {
      type: DataTypes.BOOLEAN,
      defaultValue: false,
    },
    zakatDue: {
      type: DataTypes.DECIMAL(12, 2),
      defaultValue: 0.0,
    },
  },
  { sequelize, tableName: 'zakat_calculations' }
);


// ==========================================
// ORM RELATIONSHIPS AND INDEX REGISTRATIONS
// ==========================================

Branch.hasMany(User, { foreignKey: 'branchId' });
User.belongsTo(Branch, { foreignKey: 'branchId' });

User.hasMany(Attendance, { foreignKey: 'userId' });
Attendance.belongsTo(User, { foreignKey: 'userId' });

Branch.hasMany(Inventory, { foreignKey: 'branchId' });
Inventory.belongsTo(Branch, { foreignKey: 'branchId' });

Product.hasMany(Inventory, { foreignKey: 'productId' });
Inventory.belongsTo(Product, { foreignKey: 'productId' });

Branch.hasMany(Transaction, { foreignKey: 'branchId' });
Transaction.belongsTo(Branch, { foreignKey: 'branchId' });

User.hasMany(Transaction, { foreignKey: 'cashierId' });
Transaction.belongsTo(User, { foreignKey: 'cashierId' });

Contact.hasMany(Transaction, { foreignKey: 'contactId' });
Transaction.belongsTo(Contact, { foreignKey: 'contactId' });

Transaction.hasMany(TransactionItem, { foreignKey: 'transactionId' });
TransactionItem.belongsTo(Transaction, { foreignKey: 'transactionId' });

Product.hasMany(TransactionItem, { foreignKey: 'productId' });
TransactionItem.belongsTo(Product, { foreignKey: 'productId' });


// ==========================================================
// KEY REVOLVING STATISTICAL & FINANCIAL SQL QUERIES (STORED PROCEDURES)
// ==========================================================

/**
 * 1. Stored Procedure replacement in ORM
 * Calculates branch-wise daily, weekly, monthly profits & margins
 */
export async function getProfitReport(branchId: string, startDate: Date, endDate: Date) {
  const query = `
    SELECT 
      DATE(t.transactionDate) AS "date",
      COUNT(t.id) AS total_sales_count,
      SUM(t.totalAmount) AS total_sales_revenue,
      SUM(t.totalCost) AS total_cost_of_goods,
      (SUM(t.totalAmount) - SUM(t.totalCost)) AS net_profit,
      ROUND(((SUM(t.totalAmount) - SUM(t.totalCost)) / NULLIF(SUM(t.totalAmount), 0)) * 100, 2) AS profit_margin_percentage
    FROM transactions t
    WHERE t.branchId = :branchId 
      AND t.type = 'sale'
      AND t.transactionDate BETWEEN :startDate AND :endDate
    GROUP BY DATE(t.transactionDate)
    ORDER BY DATE(t.transactionDate) ASC;
  `;
  return await sequelize.query(query, {
    replacements: { branchId, startDate, endDate },
  });
}

/**
 * 2. Stored Procedure for Multi-Branch Inventory Threshold Warnings
 */
export async function getLowStockWarningsByBranch(branchId?: string) {
  const branchFilter = branchId ? 'AND i.branchId = :branchId' : '';
  const query = `
    SELECT 
      b.name AS branch_name,
      p.id AS product_id,
      p.name AS product_name,
      p.barcode,
      p.category,
      i.stockInPieces AS stock_in_pieces,
      i.minimumRequiredStock AS min_required,
      ROUND(i.stockInPieces / CAST(p.cartonQuantity AS REAL), 1) AS stock_in_cartons,
      p.cartonQuantity
    FROM inventories i
    JOIN products p ON i.productId = p.id
    JOIN branches b ON i.branchId = b.id
    WHERE i.stockInPieces <= i.minimumRequiredStock
      ${branchFilter}
    ORDER BY (i.stockInPieces - i.minimumRequiredStock) ASC;
  `;
  return await sequelize.query(query, {
    replacements: { branchId },
  });
}

/**
 * 3. Stored Procedure for Automatic Zakat Calculations logic
 */
export async function calculateIslamicCommercialZakat(silverGramPrice: number) {
  // NISAB definition: The value of 595 grams of silver
  const nisabThreshold = 595 * silverGramPrice;

  // 1. Get liquid cash available (simulated as sum of cash paid from transactions, and reserve bank balances)
  const cashPaidQuery = await sequelize.query(`
    SELECT COALESCE(SUM(paidAmount), 0) AS total_cash FROM transactions WHERE type='sale';
  `) as any[];
  const cashPurchasesQuery = await sequelize.query(`
    SELECT COALESCE(SUM(paidAmount), 0) AS total_cash_spent FROM transactions WHERE type='purchase';
  `) as any[];

  // Assume starting bank vault is 150,000 AED/SR, adjust with transactions
  const startingVault = 250000.00;
  const cashInHand = startingVault + Number(cashPaidQuery[0][0].total_cash) - Number(cashPurchasesQuery[0][0].total_cash_spent);

  // 2. Get current value of Zakat-eligible inventory (stockInPieces * product.unitCost)
  const inventoryQuery = await sequelize.query(`
    SELECT 
      COALESCE(SUM(i.stockInPieces * p.unitCost), 0) AS total_goods_value
    FROM inventories i
    JOIN products p ON i.productId = p.id
    WHERE p.isZakatEligible = 1;
  `) as any[];
  const goodsValue = Number(inventoryQuery[0][0].total_goods_value);

  // 3. Collectable receivables (Customer debts)
  const receivablesQuery = await sequelize.query(`
    SELECT COALESCE(SUM(debtBalance), 0) AS value_receivable FROM contacts WHERE type='customer';
  `) as any[];
  const collectableReceivables = Number(receivablesQuery[0][0].value_receivable);

  // 4. Deductible payables (Supplier outstanding debts)
  const payablesQuery = await sequelize.query(`
    SELECT COALESCE(SUM(debtBalance), 0) AS value_payable FROM contacts WHERE type='supplier';
  `) as any[];
  const deductiblePayables = Number(payablesQuery[0][0].value_payable);

  // CALCULATE BASE
  const zakatNetBase = (cashInHand + goodsValue + collectableReceivables) - deductiblePayables;
  const isNisabReached = zakatNetBase >= nisabThreshold;
  const zakatDue = isNisabReached ? (zakatNetBase * 0.025) : 0;

  // Log calculation results into database
  const zakatLog = await ZakatCalculation.create({
    calculationDate: new Date(),
    cashInHand,
    goodsValue,
    collectableReceivables,
    deductiblePayables,
    nisabValueSilverGrams: nisabThreshold,
    isNisabReached,
    zakatDue: zakatDue > 0 ? zakatDue : 0,
  });

  return zakatLog;
}
