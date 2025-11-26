/**
 * Copyright © 2018 spring-data-dynamodb (https://github.com/prasanna0586/spring-data-dynamodb)
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
package org.socialsignin.spring.data.dynamodb.domain.sample;

import org.socialsignin.spring.data.dynamodb.marshaller.Date2EpocheAttributeConverter;
import org.socialsignin.spring.data.dynamodb.marshaller.Date2IsoAttributeConverter;
import org.socialsignin.spring.data.dynamodb.marshaller.Instant2EpocheAttributeConverter;
import org.socialsignin.spring.data.dynamodb.marshaller.Instant2IsoAttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Comprehensive test entity for validating type converters in both V1_COMPATIBLE and V2_NATIVE modes.
 *
 * This entity includes all DynamoDB-supported types to ensure proper conversion and marshalling
 * across different modes.
 * @author Prasanna Kumar Ramachandran
 */
@DynamoDbBean
public class TypeTestEntity {

    private String id;

    // Boolean types - both primitive and wrapper
    private boolean primitiveBoolean;
    private Boolean wrapperBoolean;

    // Numeric types - primitives
    private byte primitiveByte;
    private short primitiveShort;
    private int primitiveInt;
    private long primitiveLong;
    private float primitiveFloat;
    private double primitiveDouble;

    // Numeric types - wrappers (can be null)
    private Byte wrapperByte;
    private Short wrapperShort;
    private Integer wrapperInteger;
    private Long wrapperLong;
    private Float wrapperFloat;
    private Double wrapperDouble;

    // Big number types
    private BigDecimal bigDecimalValue;
    private BigInteger bigIntegerValue;

    // String
    private String stringValue;

    // Enum
    private TaskStatus enumValue;

    // Date/Time types
    private Date dateValue;
    private Instant instantValue;

    // Date/Time types - epoch variants
    private Date dateEpochValue;
    private Instant instantEpochValue;

    // Collection types - String
    private List<String> stringList;
    private Set<String> stringSet;
    private Map<String, String> stringMap;

    // Collection types - Numbers
    private List<Integer> integerList;
    private Set<Integer> integerSet;
    private Map<String, Integer> integerMap;

    // Collection types - Mixed
    private List<Double> doubleList;
    private Set<Long> longSet;
    private Map<String, Boolean> booleanMap;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("id")
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @DynamoDbAttribute("primitiveBoolean")
    public boolean isPrimitiveBoolean() {
        return primitiveBoolean;
    }

    public void setPrimitiveBoolean(boolean primitiveBoolean) {
        this.primitiveBoolean = primitiveBoolean;
    }

    @DynamoDbAttribute("wrapperBoolean")
    public Boolean getWrapperBoolean() {
        return wrapperBoolean;
    }

    public void setWrapperBoolean(Boolean wrapperBoolean) {
        this.wrapperBoolean = wrapperBoolean;
    }

    @DynamoDbAttribute("primitiveByte")
    public byte getPrimitiveByte() {
        return primitiveByte;
    }

    public void setPrimitiveByte(byte primitiveByte) {
        this.primitiveByte = primitiveByte;
    }

    @DynamoDbAttribute("primitiveShort")
    public short getPrimitiveShort() {
        return primitiveShort;
    }

    public void setPrimitiveShort(short primitiveShort) {
        this.primitiveShort = primitiveShort;
    }

    @DynamoDbAttribute("primitiveInt")
    public int getPrimitiveInt() {
        return primitiveInt;
    }

    public void setPrimitiveInt(int primitiveInt) {
        this.primitiveInt = primitiveInt;
    }

    @DynamoDbAttribute("primitiveLong")
    public long getPrimitiveLong() {
        return primitiveLong;
    }

    public void setPrimitiveLong(long primitiveLong) {
        this.primitiveLong = primitiveLong;
    }

    @DynamoDbAttribute("primitiveFloat")
    public float getPrimitiveFloat() {
        return primitiveFloat;
    }

    public void setPrimitiveFloat(float primitiveFloat) {
        this.primitiveFloat = primitiveFloat;
    }

    @DynamoDbAttribute("primitiveDouble")
    public double getPrimitiveDouble() {
        return primitiveDouble;
    }

    public void setPrimitiveDouble(double primitiveDouble) {
        this.primitiveDouble = primitiveDouble;
    }

    @DynamoDbAttribute("wrapperByte")
    public Byte getWrapperByte() {
        return wrapperByte;
    }

    public void setWrapperByte(Byte wrapperByte) {
        this.wrapperByte = wrapperByte;
    }

    @DynamoDbAttribute("wrapperShort")
    public Short getWrapperShort() {
        return wrapperShort;
    }

    public void setWrapperShort(Short wrapperShort) {
        this.wrapperShort = wrapperShort;
    }

    @DynamoDbAttribute("wrapperInteger")
    public Integer getWrapperInteger() {
        return wrapperInteger;
    }

    public void setWrapperInteger(Integer wrapperInteger) {
        this.wrapperInteger = wrapperInteger;
    }

    @DynamoDbAttribute("wrapperLong")
    public Long getWrapperLong() {
        return wrapperLong;
    }

    public void setWrapperLong(Long wrapperLong) {
        this.wrapperLong = wrapperLong;
    }

    @DynamoDbAttribute("wrapperFloat")
    public Float getWrapperFloat() {
        return wrapperFloat;
    }

    public void setWrapperFloat(Float wrapperFloat) {
        this.wrapperFloat = wrapperFloat;
    }

    @DynamoDbAttribute("wrapperDouble")
    public Double getWrapperDouble() {
        return wrapperDouble;
    }

    public void setWrapperDouble(Double wrapperDouble) {
        this.wrapperDouble = wrapperDouble;
    }

    @DynamoDbAttribute("bigDecimalValue")
    public BigDecimal getBigDecimalValue() {
        return bigDecimalValue;
    }

    public void setBigDecimalValue(BigDecimal bigDecimalValue) {
        this.bigDecimalValue = bigDecimalValue;
    }

    @DynamoDbAttribute("bigIntegerValue")
    public BigInteger getBigIntegerValue() {
        return bigIntegerValue;
    }

    public void setBigIntegerValue(BigInteger bigIntegerValue) {
        this.bigIntegerValue = bigIntegerValue;
    }

    @DynamoDbAttribute("stringValue")
    public String getStringValue() {
        return stringValue;
    }

    public void setStringValue(String stringValue) {
        this.stringValue = stringValue;
    }

    @DynamoDbAttribute("enumValue")
    public TaskStatus getEnumValue() {
        return enumValue;
    }

    public void setEnumValue(TaskStatus enumValue) {
        this.enumValue = enumValue;
    }

    @DynamoDbAttribute("dateValue")
    @DynamoDbConvertedBy(Date2IsoAttributeConverter.class)
    public Date getDateValue() {
        return dateValue;
    }

    public void setDateValue(Date dateValue) {
        this.dateValue = dateValue;
    }

    @DynamoDbAttribute("instantValue")
    @DynamoDbConvertedBy(Instant2IsoAttributeConverter.class)
    public Instant getInstantValue() {
        return instantValue;
    }

    public void setInstantValue(Instant instantValue) {
        this.instantValue = instantValue;
    }

    @DynamoDbAttribute("dateEpochValue")
    @DynamoDbConvertedBy(Date2EpocheAttributeConverter.class)
    public Date getDateEpochValue() {
        return dateEpochValue;
    }

    public void setDateEpochValue(Date dateEpochValue) {
        this.dateEpochValue = dateEpochValue;
    }

    @DynamoDbAttribute("instantEpochValue")
    @DynamoDbConvertedBy(Instant2EpocheAttributeConverter.class)
    public Instant getInstantEpochValue() {
        return instantEpochValue;
    }

    public void setInstantEpochValue(Instant instantEpochValue) {
        this.instantEpochValue = instantEpochValue;
    }

    @DynamoDbAttribute("stringList")
    public List<String> getStringList() {
        return stringList;
    }

    public void setStringList(List<String> stringList) {
        this.stringList = stringList;
    }

    @DynamoDbAttribute("stringSet")
    public Set<String> getStringSet() {
        return stringSet;
    }

    public void setStringSet(Set<String> stringSet) {
        this.stringSet = stringSet;
    }

    @DynamoDbAttribute("stringMap")
    public Map<String, String> getStringMap() {
        return stringMap;
    }

    public void setStringMap(Map<String, String> stringMap) {
        this.stringMap = stringMap;
    }

    @DynamoDbAttribute("integerList")
    public List<Integer> getIntegerList() {
        return integerList;
    }

    public void setIntegerList(List<Integer> integerList) {
        this.integerList = integerList;
    }

    @DynamoDbAttribute("integerSet")
    public Set<Integer> getIntegerSet() {
        return integerSet;
    }

    public void setIntegerSet(Set<Integer> integerSet) {
        this.integerSet = integerSet;
    }

    @DynamoDbAttribute("integerMap")
    public Map<String, Integer> getIntegerMap() {
        return integerMap;
    }

    public void setIntegerMap(Map<String, Integer> integerMap) {
        this.integerMap = integerMap;
    }

    @DynamoDbAttribute("doubleList")
    public List<Double> getDoubleList() {
        return doubleList;
    }

    public void setDoubleList(List<Double> doubleList) {
        this.doubleList = doubleList;
    }

    @DynamoDbAttribute("longSet")
    public Set<Long> getLongSet() {
        return longSet;
    }

    public void setLongSet(Set<Long> longSet) {
        this.longSet = longSet;
    }

    @DynamoDbAttribute("booleanMap")
    public Map<String, Boolean> getBooleanMap() {
        return booleanMap;
    }

    public void setBooleanMap(Map<String, Boolean> booleanMap) {
        this.booleanMap = booleanMap;
    }
}
