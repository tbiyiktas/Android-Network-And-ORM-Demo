package lib.net;

import java.io.File;
import java.lang.reflect.Type;
import java.util.HashMap;

/**
 * Tüm API çağrıları için temel sınıf.
 * Bu sınıf, NetworkManager'ı kullanarak istekleri yönetir ve iş mantığını basitleştirir.
 */
public abstract class ABaseApi {

    protected String baseUrl;
    protected NetworkManager networkManager;

    /**
     * API client'ını başlatmak için kullanılan constructor.
     * @param baseUrl API'nin ana adresi.
     */
    public ABaseApi(String baseUrl) {
        this.baseUrl = baseUrl;
        this.networkManager = NetworkManager.create(baseUrl);
    }

    /**
     * GET metodu için wrapper.
     * @param relativePath Göreceli URL yolu (örneğin: "/todos").
     * @param queryParams URL sorgu parametreleri.
     * @param headers İstek başlıkları.
     * @param responseType Yanıt verisinin dönüş tipi.
     * @param callback Asenkron yanıtı işlemek için geri çağrı.
     */
    public <T> RequestHandle get(
            String relativePath,
            HashMap<String, String> queryParams,
            HashMap<String, String> headers,
            Type responseType,
            NetworkCallback<T> callback) {

        return networkManager.get(relativePath, queryParams, headers, responseType, callback);
    }

    /**
     * POST metodu için wrapper.
     * @param relativePath Göreceli URL yolu.
     * @param jsonContent İstek gövdesinde gönderilecek JSON verisi.
     * @param headers İstek başlıkları.
     * @param responseType Yanıt verisinin dönüş tipi.
     * @param callback Asenkron yanıtı işlemek için geri çağrı.
     */
    public <T> RequestHandle post(
            String relativePath,
            String jsonContent,
            HashMap<String, String> headers,
            Type responseType,
            NetworkCallback<T> callback) {

        return networkManager.post(relativePath, jsonContent, headers, responseType, callback);
    }

    /**
     * PUT metodu için wrapper.
     * @param relativePath Göreceli URL yolu.
     * @param jsonContent İstek gövdesinde gönderilecek JSON verisi.
     * @param headers İstek başlıkları.
     * @param responseType Yanıt verisinin dönüş tipi.
     * @param callback Asenkron yanıtı işlemek için geri çağrı.
     */
    public <T> RequestHandle put(
            String relativePath,
            String jsonContent,
            HashMap<String, String> headers,
            Type responseType,
            NetworkCallback<T> callback) {

        return networkManager.put(relativePath, jsonContent, headers, responseType, callback);
    }

    /**
     * DELETE metodu için wrapper.
     * @param relativePath Göreceli URL yolu.
     * @param headers İstek başlıkları.
     * @param responseType Yanıt verisinin dönüş tipi.
     * @param callback Asenkron yanıtı işlemek için geri çağrı.
     */
    public <T> RequestHandle delete(
            String relativePath,
            HashMap<String, String> headers,
            Type responseType,
            NetworkCallback<T> callback) {

        return networkManager.delete(relativePath, headers, responseType, callback);
    }

    /**
     * Multipart dosya yükleme için wrapper.
     * @param relativePath Göreceli URL yolu.
     * @param formFields Diğer form alanları.
     * @param files Yüklenecek dosyaların haritası.
     * @param headers İstek başlıkları.
     * @param responseType Yanıt verisinin dönüş tipi.
     * @param callback Asenkron yanıtı işlemek için geri çağrı.
     */
    public <T> RequestHandle upload(
            String relativePath,
            HashMap<String, String> formFields,
            HashMap<String, File> files,
            HashMap<String, String> headers,
            Type responseType,
            NetworkCallback<T> callback) {

        return networkManager.upload(relativePath, formFields, files, headers, responseType, callback);
    }
}