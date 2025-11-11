package org.socialsignin.spring.data.dynamodb.domain.sample;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.socialsignin.spring.data.dynamodb.core.DynamoDBOperations;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ComparisonOperator;
import software.amazon.awssdk.services.dynamodb.model.Condition;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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

                DocumentMetadata gsiKey = new DocumentMetadata();
                gsiKey.setMemberId(memberId);

                DynamoDBQueryExpression<DocumentMetadata> query = new DynamoDBQueryExpression<DocumentMetadata>()
                    .withIndexName("memberId-documentCategory-index")
                    .withConsistentRead(false)
                    .withHashKeyValues(gsiKey) // Pass the object with memberId as hash key
                    .withRangeKeyCondition("documentCategory", Condition.builder()
                        .comparisonOperator(ComparisonOperator.EQ)
                        .attributeValueList(AttributeValue.builder().n(category.toString())
                                .build())
                        .build());

                PaginatedQueryList<DocumentMetadata> results = dynamoDBOperations.query(DocumentMetadata.class, query);
                return new ArrayList<>(results);

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

                DocumentMetadata gsiKey = new DocumentMetadata();
                gsiKey.setMemberId(memberId);

                DynamoDBQueryExpression<DocumentMetadata> query = new DynamoDBQueryExpression<DocumentMetadata>()
                    .withIndexName("memberId-documentSubCategory-index")
                    .withConsistentRead(false)
                    .withHashKeyValues(gsiKey) // Pass the object with memberId as hash key
                    .withRangeKeyCondition("documentSubCategory", Condition.builder()
                        .comparisonOperator(ComparisonOperator.EQ)
                        .attributeValueList(AttributeValue.builder().n(subCategory.toString())
                                .build())
                        .build());

                PaginatedQueryList<DocumentMetadata> results = dynamoDBOperations.query(DocumentMetadata.class, query);
                return new ArrayList<>(results);

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
