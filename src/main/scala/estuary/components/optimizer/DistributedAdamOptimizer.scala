package estuary.components.optimizer

import breeze.linalg.DenseMatrix
import estuary.components.Exception.GradientExplosionException
import estuary.components.optimizer.AdamOptimizer.AdamParam
import estuary.model.Model
import org.apache.log4j.Logger

class DistributedAdamOptimizer(override val iteration: Int,
                               override val learningRate: Double,
                               override val paramSavePath: String,
                               override val miniBatchSize: Int,
                               override val momentumRate: Double,
                               override val adamRate: Double,
                               val nTasks: Int)
  extends AdamOptimizer(iteration, learningRate, paramSavePath, miniBatchSize, momentumRate, adamRate)
    with AbstractDistributed[AdamParam, Seq[DenseMatrix[Double]]] {
  override val logger: Logger = Logger.getLogger(this.getClass)

  protected var parameterServer: AdamParam = _

  protected def updateParameterServer(grads: AdamParam, miniBatchTime: Int): Unit = {
    val nowParams = fetchParameterServer()
    val newParams = updateFunc(nowParams, grads.modelParam, miniBatchTime)
    updateParameterServer(newParams)
  }

  def parOptimize(feature: DenseMatrix[Double], label: DenseMatrix[Double], model: Model[Seq[DenseMatrix[Double]]], initParams: Seq[DenseMatrix[Double]]): Seq[DenseMatrix[Double]] = {
    updateParameterServer(AdamParam(initParams, getInitAdam(initParams), getInitAdam(initParams)))

    val parBatches = genParBatches(feature, label)
    val modelInstances = parBatches.map(_ => model.copyStructure)

    for {(batch, model) <- parBatches.zip(modelInstances)
         i <- (0 until iteration).toIterator
         ((feature, label), miniBatchTime) <- getMiniBatches(batch._1, batch._2).zipWithIndex
    } {
      val printMiniBatchUnit = math.max(feature.rows / this.miniBatchSize / 5, 10)
      val params = fetchParameterServer()
      val cost = model.forward(feature, label, params.modelParam)

      printCostInfo(cost, i, miniBatchTime, printMiniBatchUnit)

      //save cost and check for gradient explosion every 10 iterations
      if (miniBatchTime % 10 == 0) {
        addCostHistory(cost)
        try {
          checkGradientsExplosion(cost, minCost)
        } catch {
          case e: GradientExplosionException =>
            handleGradientExplosionException(params, paramSavePath)
            if (exceptionCount > 10) throw e
        }
      }

      val grads = model.backward(label, params.modelParam)
      updateParameterServer(AdamParam(grads, null, null), miniBatchTime)
    }


    parameterServer.modelParam
  }
}

object DistributedAdamOptimizer {
  def apply(iteration: Int = 100, learningRate: Double = 0.001, paramSavePath: String = System.getProperty("user.dir"), miniBatchSize: Int = 64, momentumRate: Double = 0.9, adamRate: Double = 0.999, nTasks: Int = 4): DistributedAdamOptimizer = {
    new DistributedAdamOptimizer(iteration, learningRate, paramSavePath, miniBatchSize, momentumRate, adamRate, nTasks)
  }
}

