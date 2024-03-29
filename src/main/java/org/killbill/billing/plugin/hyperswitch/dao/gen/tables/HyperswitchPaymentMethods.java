/*
 * This file is generated by jOOQ.
 */
package org.killbill.billing.plugin.hyperswitch.dao.gen.tables;


import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Identity;
import org.jooq.Index;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Row10;
import org.jooq.Schema;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.TableOptions;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.TableImpl;
import org.jooq.types.ULong;
import org.killbill.billing.plugin.hyperswitch.dao.gen.Indexes;
import org.killbill.billing.plugin.hyperswitch.dao.gen.Keys;
import org.killbill.billing.plugin.hyperswitch.dao.gen.Killbill;
import org.killbill.billing.plugin.hyperswitch.dao.gen.tables.records.HyperswitchPaymentMethodsRecord;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class HyperswitchPaymentMethods extends TableImpl<HyperswitchPaymentMethodsRecord> {

    private static final long serialVersionUID = -18202213;

    /**
     * The reference instance of <code>killbill.hyperswitch_payment_methods</code>
     */
    public static final HyperswitchPaymentMethods HYPERSWITCH_PAYMENT_METHODS = new HyperswitchPaymentMethods();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<HyperswitchPaymentMethodsRecord> getRecordType() {
        return HyperswitchPaymentMethodsRecord.class;
    }

    /**
     * The column <code>killbill.hyperswitch_payment_methods.record_id</code>.
     */
    public final TableField<HyperswitchPaymentMethodsRecord, ULong> RECORD_ID = createField(DSL.name("record_id"), org.jooq.impl.SQLDataType.BIGINTUNSIGNED.nullable(false).identity(true), this, "");

    /**
     * The column <code>killbill.hyperswitch_payment_methods.kb_account_id</code>.
     */
    public final TableField<HyperswitchPaymentMethodsRecord, String> KB_ACCOUNT_ID = createField(DSL.name("kb_account_id"), org.jooq.impl.SQLDataType.CHAR(36).nullable(false), this, "");

    /**
     * The column <code>killbill.hyperswitch_payment_methods.kb_payment_method_id</code>.
     */
    public final TableField<HyperswitchPaymentMethodsRecord, String> KB_PAYMENT_METHOD_ID = createField(DSL.name("kb_payment_method_id"), org.jooq.impl.SQLDataType.CHAR(36).nullable(false), this, "");

    /**
     * The column <code>killbill.hyperswitch_payment_methods.hyperswitch_id</code>.
     */
    public final TableField<HyperswitchPaymentMethodsRecord, String> HYPERSWITCH_ID = createField(DSL.name("hyperswitch_id"), org.jooq.impl.SQLDataType.VARCHAR(255).nullable(false), this, "");

    /**
     * The column <code>killbill.hyperswitch_payment_methods.is_default</code>.
     */
    public final TableField<HyperswitchPaymentMethodsRecord, Short> IS_DEFAULT = createField(DSL.name("is_default"), org.jooq.impl.SQLDataType.SMALLINT.nullable(false).defaultValue(org.jooq.impl.DSL.inline("0", org.jooq.impl.SQLDataType.SMALLINT)), this, "");

    /**
     * The column <code>killbill.hyperswitch_payment_methods.is_deleted</code>.
     */
    public final TableField<HyperswitchPaymentMethodsRecord, Short> IS_DELETED = createField(DSL.name("is_deleted"), org.jooq.impl.SQLDataType.SMALLINT.nullable(false).defaultValue(org.jooq.impl.DSL.inline("0", org.jooq.impl.SQLDataType.SMALLINT)), this, "");

    /**
     * The column <code>killbill.hyperswitch_payment_methods.additional_data</code>.
     */
    public final TableField<HyperswitchPaymentMethodsRecord, String> ADDITIONAL_DATA = createField(DSL.name("additional_data"), org.jooq.impl.SQLDataType.CLOB, this, "");

    /**
     * The column <code>killbill.hyperswitch_payment_methods.created_date</code>.
     */
    public final TableField<HyperswitchPaymentMethodsRecord, LocalDateTime> CREATED_DATE = createField(DSL.name("created_date"), org.jooq.impl.SQLDataType.LOCALDATETIME.nullable(false), this, "");

    /**
     * The column <code>killbill.hyperswitch_payment_methods.updated_date</code>.
     */
    public final TableField<HyperswitchPaymentMethodsRecord, LocalDateTime> UPDATED_DATE = createField(DSL.name("updated_date"), org.jooq.impl.SQLDataType.LOCALDATETIME.nullable(false), this, "");

    /**
     * The column <code>killbill.hyperswitch_payment_methods.kb_tenant_id</code>.
     */
    public final TableField<HyperswitchPaymentMethodsRecord, String> KB_TENANT_ID = createField(DSL.name("kb_tenant_id"), org.jooq.impl.SQLDataType.CHAR(36).nullable(false), this, "");

    /**
     * Create a <code>killbill.hyperswitch_payment_methods</code> table reference
     */
    public HyperswitchPaymentMethods() {
        this(DSL.name("hyperswitch_payment_methods"), null);
    }

    /**
     * Create an aliased <code>killbill.hyperswitch_payment_methods</code> table reference
     */
    public HyperswitchPaymentMethods(String alias) {
        this(DSL.name(alias), HYPERSWITCH_PAYMENT_METHODS);
    }

    /**
     * Create an aliased <code>killbill.hyperswitch_payment_methods</code> table reference
     */
    public HyperswitchPaymentMethods(Name alias) {
        this(alias, HYPERSWITCH_PAYMENT_METHODS);
    }

    private HyperswitchPaymentMethods(Name alias, Table<HyperswitchPaymentMethodsRecord> aliased) {
        this(alias, aliased, null);
    }

    private HyperswitchPaymentMethods(Name alias, Table<HyperswitchPaymentMethodsRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
    }

    public <O extends Record> HyperswitchPaymentMethods(Table<O> child, ForeignKey<O, HyperswitchPaymentMethodsRecord> key) {
        super(child, key, HYPERSWITCH_PAYMENT_METHODS);
    }

    @Override
    public Schema getSchema() {
        return Killbill.KILLBILL;
    }

    @Override
    public List<Index> getIndexes() {
        return Arrays.<Index>asList(Indexes.HYPERSWITCH_PAYMENT_METHODS_HYPERSWITCH_PAYMENT_METHODS_HYPERSWITCH_ID);
    }

    @Override
    public Identity<HyperswitchPaymentMethodsRecord, ULong> getIdentity() {
        return Keys.IDENTITY_HYPERSWITCH_PAYMENT_METHODS;
    }

    @Override
    public UniqueKey<HyperswitchPaymentMethodsRecord> getPrimaryKey() {
        return Keys.KEY_HYPERSWITCH_PAYMENT_METHODS_PRIMARY;
    }

    @Override
    public List<UniqueKey<HyperswitchPaymentMethodsRecord>> getKeys() {
        return Arrays.<UniqueKey<HyperswitchPaymentMethodsRecord>>asList(Keys.KEY_HYPERSWITCH_PAYMENT_METHODS_PRIMARY, Keys.KEY_HYPERSWITCH_PAYMENT_METHODS_RECORD_ID, Keys.KEY_HYPERSWITCH_PAYMENT_METHODS_HYPERSWITCH_PAYMENT_METHODS_KB_PAYMENT_ID);
    }

    @Override
    public HyperswitchPaymentMethods as(String alias) {
        return new HyperswitchPaymentMethods(DSL.name(alias), this);
    }

    @Override
    public HyperswitchPaymentMethods as(Name alias) {
        return new HyperswitchPaymentMethods(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public HyperswitchPaymentMethods rename(String name) {
        return new HyperswitchPaymentMethods(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public HyperswitchPaymentMethods rename(Name name) {
        return new HyperswitchPaymentMethods(name, null);
    }

    // -------------------------------------------------------------------------
    // Row10 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row10<ULong, String, String, String, Short, Short, String, LocalDateTime, LocalDateTime, String> fieldsRow() {
        return (Row10) super.fieldsRow();
    }
}
