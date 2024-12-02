package org.qortal.block;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.persistence.jaxb.JAXBContextFactory;
import org.eclipse.persistence.jaxb.UnmarshallerProperties;
import org.qortal.crypto.Crypto;
import org.qortal.data.account.AccountData;
import org.qortal.data.account.AccountBlocksMintedAdjustmentData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.utils.Base58;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.UnmarshalException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public final class RunBlocksMintedAdjustment {
    private static final Logger LOGGER = LogManager.getLogger(RunBlocksMintedAdjustment.class);
    private static final String BLOCKS_MINTED_ADJUSTMENT_SOURCE = "blocks-minted-adjustment.json";
    private static final String BLOCKS_MINTED_ADJUSTMENT_HASH = BlockChain.getInstance().getBlocksMintedAdjustmentHash();
    private static final List<AccountBlocksMintedAdjustmentData> adjustments = accountsBlocksMintedAdjustments();

    private RunBlocksMintedAdjustment() {
        // Do not instantiate
    }

    @SuppressWarnings("unchecked")
    private static List<AccountBlocksMintedAdjustmentData> accountsBlocksMintedAdjustments() {
        Unmarshaller unmarshaller;

        try {
            // Create JAXB context aware of classes we need to unmarshal
            JAXBContext pf = JAXBContextFactory.createContext(new Class[] {
                    AccountBlocksMintedAdjustmentData.class
            }, null);
            // Create unmarshaller
            unmarshaller = pf.createUnmarshaller();
            // Set the unmarshaller media type to JSON
            unmarshaller.setProperty(UnmarshallerProperties.MEDIA_TYPE, "application/json");
            // Tell unmarshaller that there's no JSON root element in the JSON input
            unmarshaller.setProperty(UnmarshallerProperties.JSON_INCLUDE_ROOT, false);
        } catch (JAXBException e) {
            String message = "Failed to setup unmarshaller for blocks minted adjustment";
            LOGGER.error(message, e);
            throw new RuntimeException(message, e);
        }

        ClassLoader classLoader = BlockChain.class.getClassLoader();
        InputStream pfIn = classLoader.getResourceAsStream(BLOCKS_MINTED_ADJUSTMENT_SOURCE);
        StreamSource pfSource = new StreamSource(pfIn);

        try  {
            // Attempt to unmarshal JSON stream to BlockChain config
            return (List<AccountBlocksMintedAdjustmentData>) unmarshaller.unmarshal(pfSource, AccountBlocksMintedAdjustmentData.class).getValue();
        } catch (UnmarshalException e) {
            String message = "Failed to parse blocks minted adjustment";
            LOGGER.error(message, e);
            throw new RuntimeException(message, e);
        } catch (JAXBException e) {
            String message = "Unexpected JAXB issue while processing blocks minted adjustments";
            LOGGER.error(message, e);
            throw new RuntimeException(message, e);
        }
    }

    public static void processBlocksMintedAdjustment(Block block) throws DataException {
        // Create remove blocks minted adjustment list
        List<AccountBlocksMintedAdjustmentData> removeBlocksMintedAdjustment = adjustments.stream()
                .map(adjustment -> new AccountBlocksMintedAdjustmentData(adjustment.getAddress(), -adjustment.getBlocksMintedAdjustment()))
                .collect(Collectors.toList());
        Set<AccountBlocksMintedAdjustmentData> adjustmentRemove = new HashSet<>(removeBlocksMintedAdjustment);
        final String BLOCKS_MINTED_ADJUSTMENT_HASH_VERIFY = getHash(adjustmentRemove.stream().map(AccountBlocksMintedAdjustmentData::getAddress).collect(Collectors.toList()));

        // Verify if we are on same blocks minted adjustment hash
        if (BLOCKS_MINTED_ADJUSTMENT_HASH.equals(BLOCKS_MINTED_ADJUSTMENT_HASH_VERIFY)) {
            LOGGER.trace("Verify hash passed! Running process blocks minted adjustment- this will take a while...");
            long startTime = System.currentTimeMillis();
            block.repository.getAccountRepository().updateBlocksMintedAdjustments(adjustmentRemove);
            long totalTime = System.currentTimeMillis() - startTime;
            LOGGER.trace("{} addresses processed. Total time taken: {} seconds", removeBlocksMintedAdjustment.size(), (int)(totalTime / 1000.0f));
            int updatedCount = updateAccountLevels(block.repository, adjustmentRemove);
            LOGGER.trace("Account levels updated for {} blocks minted adjustment addresses. (Process)", updatedCount);
        } else {
            LOGGER.error("Verify hash failed! Stopping process blocks minted adjustment!");
        }
    }

    public static void orphanBlocksMintedAdjustment(Block block) throws DataException {
        // Create add blocks minted adjustment list
        List<AccountBlocksMintedAdjustmentData> addBlocksMintedAdjustment = adjustments.stream()
                .map(adjustment -> new AccountBlocksMintedAdjustmentData(adjustment.getAddress(), +adjustment.getBlocksMintedAdjustment()))
                .collect(Collectors.toList());
        Set<AccountBlocksMintedAdjustmentData> adjustmentAdd = new HashSet<>(addBlocksMintedAdjustment);
        final String BLOCKS_MINTED_ADJUSTMENT_HASH_VERIFY = getHash(adjustmentAdd.stream().map(AccountBlocksMintedAdjustmentData::getAddress).collect(Collectors.toList()));

        // Verify if we are on same blocks minted adjustment hash
        if (BLOCKS_MINTED_ADJUSTMENT_HASH.equals(BLOCKS_MINTED_ADJUSTMENT_HASH_VERIFY)) {
            LOGGER.trace("Verify hash passed! Running orphan blocks minted adjustment - this will take a while...");
            long startTime = System.currentTimeMillis();
            block.repository.getAccountRepository().updateBlocksMintedAdjustments(adjustmentAdd);
            long totalTime = System.currentTimeMillis() - startTime;
            LOGGER.trace("{} addresses orphaned. Total time taken: {} seconds", addBlocksMintedAdjustment.size(), (int)(totalTime / 1000.0f));
            int updatedCount = updateAccountLevels(block.repository, adjustmentAdd);
            LOGGER.trace("Account levels updated for {} blocks minted adjustment addresses. (Orphan)", updatedCount);
        } else {
            LOGGER.error("Verify hash failed! Stopping orphan blocks minted adjustment!");
        }
    }

    private static int updateAccountLevels(Repository repository, Set<AccountBlocksMintedAdjustmentData> accountBlocksMintedAdjustment) throws DataException {
        final List<Integer> cumulativeBlocksByLevel = BlockChain.getInstance().getCumulativeBlocksByLevel();
        final int maximumLevel = cumulativeBlocksByLevel.size() - 1;
        int updatedCount = 0;

        for (AccountBlocksMintedAdjustmentData adjustmentData : accountBlocksMintedAdjustment) {
            AccountData accountData = repository.getAccountRepository().getAccount(adjustmentData.getAddress());
            final int effectiveBlocksMinted = accountData.getBlocksMinted() + accountData.getBlocksMintedAdjustment() + accountData.getBlocksMintedPenalty();

            // Shortcut for adjustments
            if (effectiveBlocksMinted < 0) {
                accountData.setLevel(0);
                repository.getAccountRepository().setLevel(accountData);
                updatedCount++;
                LOGGER.trace(() -> String.format("Minter %s updated to level %d", accountData.getAddress(), accountData.getLevel()));
                continue;
            }

            for (int newLevel = maximumLevel; newLevel >= 0; --newLevel) {
                if (effectiveBlocksMinted >= cumulativeBlocksByLevel.get(newLevel)) {
                    accountData.setLevel(newLevel);
                    repository.getAccountRepository().setLevel(accountData);
                    updatedCount++;
                    LOGGER.trace(() -> String.format("Minter %s updated to level %d", accountData.getAddress(), accountData.getLevel()));
                    break;
                }
            }
        }

        return updatedCount;
    }

    public static String getHash(List<String> adjustmentAddresses) {
        if (adjustmentAddresses == null || adjustmentAddresses.isEmpty()) {
            return null;
        }

        Collections.sort(adjustmentAddresses);
        return Base58.encode(Crypto.digest(StringUtils.join(adjustmentAddresses).getBytes(StandardCharsets.UTF_8)));
    }
}