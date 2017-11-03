package estuary.demo

import estuary.components.layers.{DropoutLayer, ReluLayer, SoftmaxLayer}
import estuary.components.optimizer.{AdamAkkaParallelOptimizer, AdamOptimizer, DecentralizedAdamAkkaParallelOptimizer}
import estuary.data.GasCensorDataReader
import estuary.model.{FullyConnectedNNModel, Model}

/**
  * Created by mengpan on 2017/10/27.
  */
object AkkaParallelDemo extends App{
  val hiddenLayers = List(
    ReluLayer(numHiddenUnits = 128),
    DropoutLayer(0.1),
    ReluLayer(numHiddenUnits = 64))
  val outputLayer = SoftmaxLayer()
  val nnModel = new FullyConnectedNNModel(hiddenLayers, outputLayer, None)

//  val (feature, label) = new GasCensorDataReader().read("/Users/mengpan/Downloads/NewDataset/training.*")

  val trainedModel = nnModel.multiNodesParTrain(AdamAkkaParallelOptimizer(learningRate = 0.001))
//  val trainedModel = nnModel.train(feature, label, AdamOptimizer())

  val (testFeature, testLabel) = new GasCensorDataReader().read("""D:\\Users\\m_pan\\Downloads\\Dataset\\Dataset\\test.*""")
  val yPredicted = trainedModel.predictToVector(testFeature, Vector(1, 2, 3, 4, 5, 6))
  val testAccuracy = Model.accuracy(Model.convertMatrixToVector(testLabel.map(_.toInt), Vector(1, 2, 3, 4, 5, 6)), yPredicted)
  println("\n The test accuracy of this model is: " + testAccuracy)

  trainedModel.plotCostHistory()
}