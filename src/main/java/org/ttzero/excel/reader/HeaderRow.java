/*
 * Copyright (c) 2017-2019, guanquan.wang@yandex.com All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ttzero.excel.reader;

import org.ttzero.excel.annotation.ExcelColumn;
import org.ttzero.excel.annotation.IgnoreImport;
import org.ttzero.excel.annotation.RowNum;
import org.ttzero.excel.entity.ListSheet;
import org.ttzero.excel.manager.Const;
import org.ttzero.excel.util.ReflectUtil;
import org.ttzero.excel.util.StringUtil;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

import static org.ttzero.excel.entity.IWorksheetWriter.isBool;
import static org.ttzero.excel.entity.IWorksheetWriter.isChar;
import static org.ttzero.excel.entity.IWorksheetWriter.isDate;
import static org.ttzero.excel.entity.IWorksheetWriter.isInt;
import static org.ttzero.excel.entity.IWorksheetWriter.isLocalDate;
import static org.ttzero.excel.entity.IWorksheetWriter.isLocalDateTime;
import static org.ttzero.excel.entity.IWorksheetWriter.isLocalTime;
import static org.ttzero.excel.util.ReflectUtil.listDeclaredFields;
import static org.ttzero.excel.util.ReflectUtil.listDeclaredMethods;
import static org.ttzero.excel.util.ReflectUtil.mapping;
import static org.ttzero.excel.util.StringUtil.EMPTY;

/**
 * @author guanquan.wang at 2019-04-17 11:55
 */
public class HeaderRow extends Row {
    protected String[] names;
    protected Class<?> clazz;
    protected Object t;
    /* The column name and column position mapping */
    protected Map<String, Integer> mapping;
    /* Storage header column */
    protected ListSheet.EntryColumn[] columns;

    protected HeaderRow() { }

    public HeaderRow with(Row row) {
        this.names = new String[row.lc];
        this.mapping = new HashMap<>();
        for (int i = row.fc; i < row.lc; i++) {
            this.names[i] = row.getString(i);
            this.mapping.put(this.names[i], i);
        }
        // Extends from row
        this.fc = row.fc;
        this.lc = row.lc;
        this.index = row.index;
        this.cells = new Cell[this.names.length];
        for (int i = 0; i < row.fc; i++) {
            this.cells[i] = new Cell();
        }
        for (int i = row.fc; i < row.lc; i++) {
            Cell cell = new Cell();
            cell.setSv(this.names[i]);
            this.cells[i] = cell;
        }
        return this;
    }

    final boolean is(Class<?> clazz) {
        return this.clazz != null && this.clazz == clazz;
    }

    /**
     * mapping
     *
     * @param clazz the type of binding
     * @return the header row
     */
    protected HeaderRow setClass(Class<?> clazz) {
        this.clazz = clazz;
        // Parse Field
        Field[] declaredFields = listDeclaredFields(clazz);
        // Parse Method
        Map<String, Method> tmp = attachOtherColumn(clazz);

        ListSheet.EntryColumn[] columns = new ListSheet.EntryColumn[declaredFields.length + tmp.size()];

        int count = 0;
        for (int i = 0; i < declaredFields.length; i++) {
            Field f = declaredFields[i];
            f.setAccessible(true);
            String gs = f.getName();

            // The setter methods take precedence over property reflection
            Method method = tmp.get(gs);
            if (method != null) {
                ListSheet.EntryColumn column = createColumn(method);
                if (column != null) {
                    column.method = method;
                    column.colIndex = check(column.name, gs);
                    if (column.clazz == null) column.clazz = method.getParameterTypes()[0];
                    columns[count++] = column;

                    // Remove from mapper if attached
                    tmp.remove(gs);
                    continue;
                }
            }

            ListSheet.EntryColumn column = createColumn(f);
            if (column != null) {
                if (StringUtil.isEmpty(column.name)) {
                    column.name = gs;
                }
                if (method != null) {
                    column.method = method;
                    if (column.clazz == null) column.clazz = method.getParameterTypes()[0];
                } else {
                    column.field = f;
                    if (column.clazz == null) column.clazz = declaredFields[i].getType();
                }
                column.colIndex = check(column.name, gs);
                columns[count++] = column;
            }
        }

        if (!tmp.isEmpty()) {
            for (Map.Entry<String, Method> entry : tmp.entrySet()) {
                ListSheet.EntryColumn column = createColumn(entry.getValue());
                if (column == null) {
                    column = new ListSheet.EntryColumn(entry.getKey());
                }
                column.method = entry.getValue();
                column.colIndex = getIndex(column.name);
                if (column.clazz == null) column.clazz = entry.getValue().getParameterTypes()[0];
                columns[count++] = column;
            }
        }

        // Remove not mapped column
        int j = 0;
        for (int i = 0; i < count; i++) {
            ListSheet.EntryColumn column = columns[i];
            if (column.colIndex >= 0 || column.clazz == RowNum.class) {
                if (i != j) {
                    columns[j] = column;
                }
                j++;
            }
        }

        this.columns = j < columns.length ? Arrays.copyOf(columns, j) : columns;

        return this;
    }

    protected int methodMapping(Class<?> clazz, Method[] writeMethods, Map<String, Method> tmp) {
        try {
            PropertyDescriptor[] propertyDescriptors = Introspector.getBeanInfo(clazz)
                .getPropertyDescriptors();
            Method[] allMethods = clazz.getMethods()
                , mergedMethods = new Method[propertyDescriptors.length];
            for (int i = 0; i < propertyDescriptors.length; i++) {
                Method method = propertyDescriptors[i].getWriteMethod();
                if (method == null) continue;
                int index = ReflectUtil.indexOf(allMethods, method);
                // Subclass methods have higher priority
                mergedMethods[i] = index >= 0 ? allMethods[index] : method;
            }

            return mapping(writeMethods, tmp, propertyDescriptors, mergedMethods);
        } catch (IntrospectionException e) {
            LOGGER.warn("Get {} property descriptor failed.", clazz);
        }
        return 0;
    }

    protected int check(String first, String second) {
        int n = getIndex(first);
        if (n == -1) n = getIndex(second);
        if (n == -1) {
            LOGGER.warn("{} field [{}] can't find in header {}", clazz, first, Arrays.toString(names));
        }
        return n;
    }

    /**
     * mapping and instance
     *
     * @param clazz the type of binding
     * @return the header row
     * @throws IllegalAccessException -
     * @throws InstantiationException -
     */
    protected HeaderRow setClassOnce(Class<?> clazz) throws IllegalAccessException, InstantiationException {
        setClass(clazz);
        this.t = clazz.newInstance();
        return this;
    }

    protected ListSheet.EntryColumn[] getColumns() {
        return columns;
    }

    @SuppressWarnings("unchecked")
    protected <T> T getT() {
        return (T) t;
    }

    public Class<?> getClazz() {
        return clazz;
    }

    @Override
    public CellType getCellType(int columnIndex) {
        return CellType.STRING;
    }

    /**
     * Get the column name by column index
     *
     * @param columnIndex the cell index
     * @return name of column
     */
    public String get(int columnIndex) {
        rangeCheck(columnIndex);
        return names[columnIndex];
    }

    /**
     * Returns the position in cell range
     *
     * @param columnName the column name
     * @return the position if found otherwise -1
     */
    public int getIndex(String columnName) {
        Integer index = mapping.get(columnName);
        return index != null ? index : -1;
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(" | ");
        StringBuilder buf = new StringBuilder();
        int i = 0;
        for (; names[i] == null; i++) ;
        char[] chars = new char[10];
        Arrays.fill(chars, 0, chars.length, '-');
        for (int j = i; i < names.length; i++) {
            joiner.add(names[i]);
            int n = simpleTestLength(names[i]) + (j == i || i == names.length - 1 ? 1 : 2);
            if (n > chars.length) {
                chars = new char[n];
                Arrays.fill(chars, 0, n, '-');
            } else {
                Arrays.fill(chars, 0, n, '-');
            }

            if (columns != null && i < columns.length && columns[i].clazz != RowNum.class) {
                Class<?> c = columns[i].clazz;

                // Align Center
                if (isDate(c) || isLocalDate(c) || isLocalDateTime(c) || isLocalTime(c) || isChar(c) || isBool(c)) {
                    chars[0] = chars[n - 1] = ':';
                }
                // Align Right
                else if (isInt(c)) {
                    chars[n - 1] = ':';
                }
                // Align Left
//            else;
            }
            buf.append(chars, 0, n).append('|');

        }

        buf.insert(0, joiner.toString() + Const.lineSeparator);

        return buf.toString();
    }

    protected int simpleTestLength(String name) {
        if (name == null) return 4;
        char[] chars = name.toCharArray();
        double d = 0.0;
        for (char c : chars) {
            if (c < 0x80) d++;
            else d += 1.75;
        }
        return (int) d;
    }

    void put(Row row, Object t) throws IllegalAccessException, InvocationTargetException {
        for (int i = 0; i < columns.length; i++) {
            if (columns[i].method != null)
                methodPut(i, row, t);
            else
                fieldPut(i, row, t);
        }
    }

    protected void fieldPut(int i, Row row, Object t) throws IllegalAccessException {
        ListSheet.EntryColumn ec = columns[i];
        int c = ec.colIndex;
        Class<?> fieldClazz = ec.clazz;
        if (fieldClazz == String.class) {
            ec.field.set(t, row.getString(c));
        }
        else if (fieldClazz == Integer.class) {
            ec.field.set(t, row.getInt(c));
        }
        else if (fieldClazz == Long.class) {
            ec.field.set(t, row.getLong(c));
        }
        else if (fieldClazz == java.util.Date.class || fieldClazz == java.sql.Date.class) {
            ec.field.set(t, row.getDate(c));
        }
        else if (fieldClazz == java.sql.Timestamp.class) {
            ec.field.set(t, row.getTimestamp(c));
        }
        else if (fieldClazz == Double.class) {
            ec.field.set(t, row.getDouble(c));
        }
        else if (fieldClazz == Float.class) {
            ec.field.set(t, row.getFloat(c));
        }
        else if (fieldClazz == Boolean.class) {
            ec.field.set(t, row.getBoolean(c));
        }
        else if (fieldClazz == BigDecimal.class) {
            ec.field.set(t, row.getDecimal(c));
        }
        else if (fieldClazz == int.class) {
            Integer v;
            ec.field.set(t, (v = row.getInt(c)) != null ? v : 0);
        }
        else if (fieldClazz == long.class) {
            Long v;
            ec.field.set(t, (v = row.getLong(c)) != null ? v : 0L);
        }
        else if (fieldClazz == double.class) {
            Double v;
            ec.field.set(t, (v = row.getDouble(c)) != null ? v : 0.0D);
        }
        else if (fieldClazz == float.class) {
            Float v;
            ec.field.set(t, (v = row.getFloat(c)) != null ? v : 0.0F);
        }
        else if (fieldClazz == boolean.class) {
            Boolean v;
            ec.field.set(t, (v = row.getBoolean(c)) != null ? v : false);
        }
        else if (fieldClazz == java.sql.Time.class) {
            ec.field.set(t, row.getTime(c));
        }
        else if (fieldClazz == LocalDateTime.class) {
            ec.field.set(t, row.getLocalDateTime(c));
        }
        else if (fieldClazz == LocalDate.class) {
            ec.field.set(t, row.getLocalDate(c));
        }
        else if (fieldClazz == LocalTime.class) {
            ec.field.set(t, row.getLocalTime(c));
        }
        else if (fieldClazz == Character.class) {
            ec.field.set(t, row.getChar(c));
        }
        else if (fieldClazz == Byte.class) {
            ec.field.set(t, row.getByte(c));
        }
        else if (fieldClazz == Short.class) {
            ec.field.set(t, row.getShort(c));
        }
        else if (fieldClazz == char.class) {
            Character v;
            ec.field.set(t, (v = row.getChar(c)) != null ? v : '\0');
        }
        else if (fieldClazz == byte.class) {
            Byte v;
            ec.field.set(t, (v = row.getByte(c)) != null ? v : 0);
        }
        else if (fieldClazz == short.class) {
            Short v;
            ec.field.set(t, (v = row.getShort(c)) != null ? v : 0);
        }
        else if (fieldClazz == RowNum.class) {
            ec.field.set(t, row.getRowNum());
        }
    }

    protected void methodPut(int i, Row row, Object t) throws IllegalAccessException, InvocationTargetException {
        ListSheet.EntryColumn ec = columns[i];
        int c = ec.colIndex;
        Class<?> fieldClazz = ec.clazz;
        if (fieldClazz == String.class) {
            ec.method.invoke(t, row.getString(c));
        }
        else if (fieldClazz == Integer.class) {
            ec.method.invoke(t, row.getInt(c));
        }
        else if (fieldClazz == Long.class) {
            ec.method.invoke(t, row.getLong(c));
        }
        else if (fieldClazz == java.util.Date.class || fieldClazz == java.sql.Date.class) {
            ec.method.invoke(t, row.getDate(c));
        }
        else if (fieldClazz == java.sql.Timestamp.class) {
            ec.method.invoke(t, row.getTimestamp(c));
        }
        else if (fieldClazz == Double.class) {
            ec.method.invoke(t, row.getDouble(c));
        }
        else if (fieldClazz == Float.class) {
            ec.method.invoke(t, row.getFloat(c));
        }
        else if (fieldClazz == Boolean.class) {
            ec.method.invoke(t, row.getBoolean(c));
        }
        else if (fieldClazz == BigDecimal.class) {
            ec.method.invoke(t, row.getDecimal(c));
        }
        else if (fieldClazz == int.class) {
            Integer v;
            ec.method.invoke(t, (v = row.getInt(c)) != null ? v : 0);
        }
        else if (fieldClazz == long.class) {
            Long v;
            ec.method.invoke(t, (v = row.getLong(c)) != null ? v : 0);
        }
        else if (fieldClazz == double.class) {
            Double v;
            ec.method.invoke(t, (v = row.getDouble(c)) != null ? v : 0.0D);
        }
        else if (fieldClazz == float.class) {
            Float v;
            ec.method.invoke(t, (v = row.getFloat(c)) != null ? v : 0.0F);
        }
        else if (fieldClazz == boolean.class) {
            Boolean v;
            ec.method.invoke(t, (v = row.getBoolean(c)) != null ? v : false);
        }
        else if (fieldClazz == java.sql.Time.class) {
            ec.method.invoke(t, row.getTime(c));
        }
        else if (fieldClazz == LocalDateTime.class) {
            ec.method.invoke(t, row.getLocalDateTime(c));
        }
        else if (fieldClazz == LocalDate.class) {
            ec.method.invoke(t, row.getLocalDate(c));
        }
        else if (fieldClazz == LocalTime.class) {
            ec.method.invoke(t, row.getLocalTime(c));
        }
        else if (fieldClazz == Character.class) {
            ec.method.invoke(t, row.getChar(c));
        }
        else if (fieldClazz == Byte.class) {
            ec.method.invoke(t, row.getByte(c));
        }
        else if (fieldClazz == Short.class) {
            ec.method.invoke(t, row.getShort(c));
        }
        else if (fieldClazz == char.class) {
            Character v;
            ec.method.invoke(t, (v = row.getChar(c)) != null ? v : '\0');
        }
        else if (fieldClazz == byte.class) {
            Byte v;
            ec.method.invoke(t, (v = row.getByte(c)) != null ? v : 0);
        }
        else if (fieldClazz == short.class) {
            Short v;
            ec.method.invoke(t, (v = row.getShort(c)) != null ? v : 0);
        }
        else if (fieldClazz == RowNum.class) {
            ec.method.invoke(t, row.getRowNum());
        }
    }

    /**
     * Ignore some columns, override this method to add custom filtering
     *
     * @param ao {@code Method} or {@code Field}
     * @return true if ignore current column
     */
    protected boolean ignoreColumn(AccessibleObject ao) {
        return ao.getAnnotation(IgnoreImport.class) != null;
    }

    /**
     * Attach some others column
     *
     * @param clazz Target class
     * @return a column and Write method mapping
     */
    protected Map<String, Method> attachOtherColumn(Class<?> clazz) {
        Method[] writeMethods;
        try {
            writeMethods = listDeclaredMethods(clazz, method -> method.getAnnotation(ExcelColumn.class) != null
                    || method.getAnnotation(RowNum.class) != null);
        } catch (IntrospectionException e) {
            LOGGER.warn("Get [" + clazz + "] read declared failed.", e);
            return Collections.emptyMap();
        }

        Map<String, Method> tmp = new LinkedHashMap<>();

        methodMapping(clazz, Arrays.stream(writeMethods).filter(m -> m.getParameterCount() == 1).toArray(Method[]::new), tmp);

        return tmp;
    }

    /**
     * Create column from {@link ExcelColumn} annotation
     * <p>
     * Override the method to extend custom comments
     *
     * @param ao {@link AccessibleObject} witch defined the {@code ExcelColumn} annotation
     * @return the Worksheet's {@link ListSheet.EntryColumn} information
     */
    protected ListSheet.EntryColumn createColumn(AccessibleObject ao) {
        // Filter all ignore column
        if (ignoreColumn(ao)) return null;

        ao.setAccessible(true);
        ExcelColumn ec = ao.getAnnotation(ExcelColumn.class);
        if (ec != null) {
            return new ListSheet.EntryColumn(ec.value());
        }
        // Row Num
        RowNum rowNum = ao.getAnnotation(RowNum.class);
        if (rowNum != null) {
            return new ListSheet.EntryColumn(EMPTY, RowNum.class);
        }
        return null;
    }
}
