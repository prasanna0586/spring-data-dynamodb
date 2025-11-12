# Type Marshalling Research: SDK v1 vs SDK v2

**Date:** 2025-01-12
**Purpose:** Verify type marshalling behavior differences between AWS SDK v1 and v2 before finalizing implementation

---

## Research Summary

### 1. Boolean Type

#### SDK v1 (DynamoDBMapper)
- **Default:** Number type - "0" (false) or "1" (true)
- **Source:** [AWS SDK v1 Supported Data Types](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DynamoDBMapper.DataTypes.html)
- **Quote:** "By default, booleans are serialized using the DynamoDB N (Number) type, with a value of '1' representing 'true' and a value of '0' representing 'false'"
- **Alternative:** `@DynamoDBNativeBoolean` annotation can store as BOOL type (deprecated)

#### SDK v2 (Enhanced Client)
- **Default:** BOOL type (native DynamoDB boolean)
- **Source:** [BooleanAttributeConverter](https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/enhanced/dynamodb/internal/converter/attribute/BooleanAttributeConverter.html)
- **Implementation:** `AttributeValue.builder().bool(value).build()`
- **Registered in:** [DefaultAttributeConverterProvider.java:237](https://github.com/aws/aws-sdk-java-v2/blob/master/services-custom/dynamodb-enhanced/src/main/java/software/amazon/awssdk/enhanced/dynamodb/DefaultAttributeConverterProvider.java)

#### Backward Compatibility
- **Issue:** [#1912 - BooleanAttributeConverter cannot convert N value of 0 and 1](https://github.com/aws/aws-sdk-java-v2/issues/1912)
- **Status:** RESOLVED in PR #1948
- **Solution:** SDK v2's BooleanAttributeConverter can READ legacy "0"/"1" Number values and convert to Boolean
- **Behavior:** When written back, values are stored as native BOOL type

#### Our Implementation
```java
// SDK_V1_COMPATIBLE: Number "0"/"1"
attributeValueBuilder.n(boolValue ? "1" : "0");

// SDK_V2_NATIVE: BOOL type
attributeValueBuilder.bool((Boolean) value);
```
**Status:** ✅ CORRECT

---

### 2. Instant Type (java.time.Instant)

#### SDK v1 (DynamoDBMapper)
- **Default:** NO native support
- **Custom converter required:** Must use `@DynamoDBTypeConverted` with custom converter
- **Common practice:** Store as String in ISO-8601 format
- **Source:** [StackOverflow - Best approach to handle Java8 date and time in DynamoDB](https://stackoverflow.com/questions/47218487/best-approach-to-handle-java8-date-and-time-in-dynamodb)
- **Quote:** "AWS DynamoDB Java SDK v1 DynamoDBMapper does not natively support java.time types... custom converters are needed"

#### SDK v2 (Enhanced Client)
- **Default:** String type (ISO-8601 format)
- **Converter:** `InstantAsStringAttributeConverter`
- **Source:** [InstantAsStringAttributeConverter.java](https://github.com/aws/aws-sdk-java-v2/blob/master/services-custom/dynamodb-enhanced/src/main/java/software/amazon/awssdk/enhanced/dynamodb/internal/converter/attribute/InstantAsStringAttributeConverter.java)
- **Implementation:** `AttributeValue.builder().s(input.toString()).build()`
- **Format:** ISO-8601 with nanosecond precision and UTC timezone
  - Example: `"1970-01-01T00:00:00.001Z"` for `Instant.EPOCH.plusMillis(1)`
- **Registered in:** [DefaultAttributeConverterProvider.java:237](https://github.com/aws/aws-sdk-java-v2/blob/master/services-custom/dynamodb-enhanced/src/main/java/software/amazon/awssdk/enhanced/dynamodb/DefaultAttributeConverterProvider.java)

#### Alternative Converters in SDK v2
- **Searched for:** `InstantAsIntegerAttributeConverter`, `InstantAsNumberAttributeConverter`
- **Result:** NONE FOUND in AWS SDK v2 repository
- **Conclusion:** AWS SDK v2 provides ONLY `InstantAsStringAttributeConverter` for Instant

#### Our Current Implementation
```java
// SDK_V1_COMPATIBLE: String (ISO-8601)
String marshalledDate = new Instant2IsoDynamoDBMarshaller().marshall(instant);
attributeValueBuilder.s(marshalledDate);

// SDK_V2_NATIVE: Number (epoch seconds) ❌ WRONG!
attributeValueBuilder.n(String.valueOf(instant.getEpochSecond()));
```

#### Our Marshaller
- **Class:** `Instant2IsoDynamoDBMarshaller`
- **Format:** `"yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"` (ISO-8601 with millisecond precision)
- **Compatible with:** SDK v1 custom converters and SDK v2's `InstantAsStringAttributeConverter`

**Status:** ❌ **SDK_V2_NATIVE mode is INCORRECT**
- AWS SDK v2 stores Instant as **String (ISO-8601)**, NOT Number (epoch seconds)
- We should store as String in both modes

---

### 3. Date Type (java.util.Date)

#### SDK v1 (DynamoDBMapper)
- **Default:** String type (ISO-8601 format)
- **Source:** [AWS SDK v1 Supported Data Types](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DynamoDBMapper.DataTypes.html)
- **Quote:** "Date values are stored as ISO-8601 formatted strings"

#### SDK v2 (Enhanced Client)
- **Default:** NO native support
- **Reason:** AWS recommends using `java.time` types (Instant, LocalDate, etc.) instead of legacy `java.util.Date`
- **Source:** [GitHub Issue #2092](https://github.com/aws/aws-sdk-java-v2/issues/2092)
- **AWS Response:** "For Date I believe we have good substitutes like Instant, LocalDate, LocalTime, LocalDateTime, Period, etc."

#### Our Implementation
```java
// SDK_V1_COMPATIBLE: String (ISO-8601)
String marshalledDate = new Date2IsoDynamoDBMarshaller().marshall(date);
attributeValueBuilder.s(marshalledDate);

// SDK_V2_NATIVE: Number (epoch milliseconds)
attributeValueBuilder.n(String.valueOf(date.getTime()));
```

**Status:** ✅ REASONABLE (our design choice since AWS provides no default)
- SDK v2 has no default Date converter
- Using Number (epoch milliseconds) is a reasonable choice for SDK_V2_NATIVE
- Maintains differentiation from SDK_V1_COMPATIBLE

---

## Conclusions

### What We Got Right ✅
1. **Boolean:**
   - SDK_V1_COMPATIBLE: Number "0"/"1" ✅
   - SDK_V2_NATIVE: BOOL type ✅

2. **Date (java.util.Date):**
   - SDK_V1_COMPATIBLE: String (ISO-8601) ✅
   - SDK_V2_NATIVE: Number (epoch ms) ✅ (our choice, AWS has no default)

### What We Got Wrong ❌
3. **Instant (java.time.Instant):**
   - SDK_V1_COMPATIBLE: String (ISO-8601) ✅
   - SDK_V2_NATIVE: **Should be String (ISO-8601)**, NOT Number (epoch seconds) ❌

---

## Required Fix

### Instant Handling in SDK_V2_NATIVE Mode

**Current (WRONG):**
```java
// SDK v2 native: Instant as epoch seconds in Number format
return AttributeValue.builder().n(String.valueOf(instant.getEpochSecond())).build();
```

**Should Be (CORRECT):**
```java
// SDK v2 native: Instant as ISO-8601 string (matches AWS SDK v2 default)
return AttributeValue.builder().s(instant.toString()).build();
```

**Impact:**
- SDK_V1_COMPATIBLE mode: No change needed (already correct)
- SDK_V2_NATIVE mode: Need to change Instant from Number to String

**Files to Update:**
1. `DynamoDBEntityWithHashKeyOnlyCriteria.java` - `convertToAttributeValue()` method
2. `DynamoDBEntityWithHashAndRangeKeyCriteria.java` - `convertToAttributeValue()` method
3. `AbstractDynamoDBQueryCriteria.java` - `addAttributeValue()` method and helper methods

---

## Final Type Mapping Table

| Type | SDK v1 Default | SDK v2 Enhanced Default | Our SDK_V1_COMPATIBLE | Our SDK_V2_NATIVE | Status |
|------|----------------|------------------------|----------------------|-------------------|--------|
| Boolean | Number ("0"/"1") | BOOL | Number ("0"/"1") | BOOL | ✅ Correct |
| Date | String (ISO-8601) | No default | String (ISO-8601) | Number (epoch ms) | ✅ Correct (our choice) |
| Instant | Custom (typically String) | String (ISO-8601) | String (ISO-8601) | ~~Number (epoch sec)~~ **String (ISO-8601)** | ❌ Need fix |
| Boolean List | Number Set | N/A (no BOOL set) | Number Set | Number Set | ✅ Correct |
| Date List | String Set | No default | String Set | Number Set | ✅ Correct |
| Instant List | Custom | N/A | String Set | ~~Number Set~~ **String Set** | ❌ Need fix |

---

## Verification Sources

1. **AWS SDK v2 Source Code:**
   - [DefaultAttributeConverterProvider.java](https://github.com/aws/aws-sdk-java-v2/blob/master/services-custom/dynamodb-enhanced/src/main/java/software/amazon/awssdk/enhanced/dynamodb/DefaultAttributeConverterProvider.java)
   - [InstantAsStringAttributeConverter.java](https://github.com/aws/aws-sdk-java-v2/blob/master/services-custom/dynamodb-enhanced/src/main/java/software/amazon/awssdk/enhanced/dynamodb/internal/converter/attribute/InstantAsStringAttributeConverter.java)
   - [BooleanAttributeConverter.java](https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/enhanced/dynamodb/internal/converter/attribute/BooleanAttributeConverter.html)

2. **AWS Documentation:**
   - [Supported data types for DynamoDBMapper for Java](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DynamoDBMapper.DataTypes.html)
   - [Changes in DynamoDB mapping APIs v1 to v2](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/dynamodb-mapping-api-changes.html)

3. **GitHub Issues:**
   - [#1912 - BooleanAttributeConverter compatibility](https://github.com/aws/aws-sdk-java-v2/issues/1912)
   - [#2092 - Date and ByteBuffer support](https://github.com/aws/aws-sdk-java-v2/issues/2092)

---

## Recommendation

**We need to fix Instant handling to match AWS SDK v2's actual behavior.**

SDK_V2_NATIVE should use:
- Boolean → BOOL ✅ (already correct)
- Date → Number (epoch ms) ✅ (our choice, AWS has no default)
- Instant → **String (ISO-8601)** ❌ (currently wrong, using Number)

This ensures that:
1. Users migrating to SDK v2 get the same behavior as AWS SDK v2 Enhanced Client
2. SDK_V2_NATIVE mode is truly "native" to SDK v2
3. Data written by our library can be read by AWS SDK v2 Enhanced Client and vice versa
