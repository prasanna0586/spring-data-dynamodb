package org.socialsignin.spring.data.dynamodb.domain.sample;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.socialsignin.spring.data.dynamodb.core.DynamoDBOperations;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Custom repository implementation for DocumentMetadata.
 * Demonstrates parallel query execution using CompletableFuture for efficient "IN" clause queries.
 *
 * Note: This class does NOT need @Repository annotation.
 * Spring Data automatically detects this class by naming convention: <RepositoryName>Impl
 */
@SuppressWarnings("unused") // Class is used by Spring Data at runtime via naming convention
public class DocumentMetadataRepositoryImpl implements DocumentMetadataRepositoryCustom {

    private static final Logger log = LoggerFactory.getLogger(DocumentMetadataRepositoryImpl.class);

    private final DynamoDBOperations dynamoDBOperations;

    public DocumentMetadataRepositoryImpl(DynamoDBOperations dynamoDBOperations) {
        this.dynamoDBOperations = dynamoDBOperations;
    }

    @Override
    public List<DocumentMetadata> findByMemberIdAndDocumentCategoryIn(Integer memberId, List<Integer> documentCategories) {
        log.info("Entering findByMemberIdAndDocumentCategoryIn - memberId: {}, documentCategories: {}",
                memberId, documentCategories);

        List<CompletableFuture<List<DocumentMetadata>>> futures = documentCategories.stream()
            .map(category ->

                CompletableFuture.<List<DocumentMetadata>>supplyAsync(() -> {

                // SDK v2: Build low-level QueryRequest for GSI query
                // Query on GSI: memberId-documentCategory-index with memberId (partition) and documentCategory (sort) keys
                Map<String, AttributeValue> expressionValues = new HashMap<>();
                expressionValues.put(":memberId", AttributeValue.builder().n(memberId.toString()).build());
                expressionValues.put(":category", AttributeValue.builder().n(category.toString()).build());

                QueryRequest queryRequest = QueryRequest.builder()
                    .tableName(dynamoDBOperations.getOverriddenTableName(DocumentMetadata.class, "DocumentMetadata"))
                    .indexName("memberId-documentCategory-index")
                    .keyConditionExpression("memberId = :memberId AND documentCategory = :category")
                    .expressionAttributeValues(expressionValues)
                    .consistentRead(false)
                    .build();

                PageIterable<DocumentMetadata> results = dynamoDBOperations.query(DocumentMetadata.class, queryRequest);

                // Convert PageIterable to List
                return StreamSupport.stream(results.items().spliterator(), false)
                    .collect(Collectors.toList());

            })).toList();

        // Wait for all queries to complete and flatten the results
        List<DocumentMetadata> result = futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .toList();

        log.info("Exiting findByMemberIdAndDocumentCategoryIn - memberId: {}, resultCount: {}",
                memberId, result.size());
        return result;
    }

    @Override
    public List<DocumentMetadata> findByMemberIdAndDocumentSubCategoryIn(Integer memberId, List<Integer> documentSubCategories) {
        log.info("Entering findByMemberIdAndDocumentSubCategoryIn - memberId: {}, documentSubCategories: {}",
                memberId, documentSubCategories);

        List<CompletableFuture<List<DocumentMetadata>>> futures = documentSubCategories.stream()
            .map(subCategory ->

                CompletableFuture.<List<DocumentMetadata>>supplyAsync(() -> {

                // SDK v2: Build low-level QueryRequest for GSI query
                // Query on GSI: memberId-documentSubCategory-index with memberId (partition) and documentSubCategory (sort) keys
                Map<String, AttributeValue> expressionValues = new HashMap<>();
                expressionValues.put(":memberId", AttributeValue.builder().n(memberId.toString()).build());
                expressionValues.put(":subCategory", AttributeValue.builder().n(subCategory.toString()).build());

                QueryRequest queryRequest = QueryRequest.builder()
                    .tableName(dynamoDBOperations.getOverriddenTableName(DocumentMetadata.class, "DocumentMetadata"))
                    .indexName("memberId-documentSubCategory-index")
                    .keyConditionExpression("memberId = :memberId AND documentSubCategory = :subCategory")
                    .expressionAttributeValues(expressionValues)
                    .consistentRead(false)
                    .build();

                PageIterable<DocumentMetadata> results = dynamoDBOperations.query(DocumentMetadata.class, queryRequest);

                // Convert PageIterable to List
                return StreamSupport.stream(results.items().spliterator(), false)
                    .collect(Collectors.toList());

            })).toList();

        // Wait for all queries to complete and flatten the results
        List<DocumentMetadata> result = futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .toList();

        log.info("Exiting findByMemberIdAndDocumentSubCategoryIn - memberId: {}, resultCount: {}",
                memberId, result.size());
        return result;
    }
}
