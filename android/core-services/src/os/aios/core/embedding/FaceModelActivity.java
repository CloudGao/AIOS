package os.aios.core.embedding;
public final class FaceModelActivity extends os.aios.runtime.models.OptionalModelActivity {
    @Override protected String modelId(){return "face";}
    @Override protected void installed(){BundledModels.initialize(this);}
}
