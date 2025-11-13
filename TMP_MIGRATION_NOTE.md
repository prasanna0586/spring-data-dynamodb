# Migration Strategy for DynamoDBEntityWithHashAndRangeKeyCriteria

## Problem
The SDK v1 class uses `DynamoDBQueryExpression` and `DynamoDBScanExpression` which work very differently from SDK v2.

## Solution
The code already has a working `buildQueryRequest()` method (in AbstractDynamoDBQueryCriteria) that builds SDK v2's low-level `QueryRequest`.

We'll use this for all query paths instead of trying to use the Enhanced Client's `QueryEnhancedRequest`, which is too limited for the complex conditional logic this code needs.

## Changes
1. Remove `buildQueryExpression()` method (SDK v1 specific)
2. Update `buildFinderQuery()` to always use `buildQueryRequest()`
3. Update `buildFinderCountQuery()` similarly
4. Keep using `MultipleEntityQueryRequestQuery` (which already uses `QueryRequest`)
5. Update `buildScanExpression()` to return `ScanEnhancedRequest`
