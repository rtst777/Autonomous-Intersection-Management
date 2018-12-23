package org.movsim.simulator.observer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;

class LogitRouteDecisionMaking {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(LogitRouteDecisionMaking.class);

    private static final int TOO_LARGE_EXPONENT = 100;

    static RouteAlternative selectMostProbableAlternative(Iterable<RouteAlternative> alternatives, double random) {
        Preconditions.checkArgument(random >= 0 && random < 1);
        double sumProb = 0;
        for (RouteAlternative alternative : alternatives) {
            sumProb += alternative.getProbability();
            LOG.debug("alternative={}, sumProb={}", alternative.toString(), sumProb);
            if (random <= sumProb) {
                return alternative;
            }
        }
        Preconditions.checkState(false, "probabilities not summed correctly: random=" + random + ", sumProb=" + sumProb);
        return null;
    }

    private static boolean hasTooLargeExponent(double beta, RouteAlternative alternative,
            Iterable<RouteAlternative> alternatives) {
        for (RouteAlternative otherAlternative : alternatives) {
            double delta = Math.abs(alternative.getDisutility() - otherAlternative.getDisutility());
            if (beta * delta > TOO_LARGE_EXPONENT) {
                return true;
            }
        }
        return false;
    }

    static void calcProbabilities(Iterable<RouteAlternative> alternatives, double uncertainty) {
        if (uncertainty > 0) {
            calcProbabilityIfStochastic(alternatives, uncertainty);
        } else {
            calcProbabilityForDeterministic(alternatives);
        }
    }

    private static void calcProbabilityIfStochastic(Iterable<RouteAlternative> alternatives, double uncertainty) {
        final double beta = -1 / uncertainty;
        for (RouteAlternative alternative : alternatives) {
            // check first for large exponential
            if (hasTooLargeExponent(beta, alternative, alternatives)) {
                // probability of 0 as trivial result
                alternative.setProbability(0);
            } else {
                double probAlternative = calcProbability(beta, alternative, alternatives);
                alternative.setProbability(probAlternative);
            }
            LOG.debug("calculated prob for stochastic case: {}", alternative);
        }
    }

    private static double calcProbability(double beta, RouteAlternative alternative,
            Iterable<RouteAlternative> alternatives) {
        double denom = 0;
        for (RouteAlternative otherAlternative : alternatives) {
            denom += Math.exp(beta * (otherAlternative.getDisutility() - alternative.getDisutility()));
        }
        return 1. / denom;
    }

    private static void calcProbabilityForDeterministic(Iterable<RouteAlternative> alternatives) {
        RouteAlternative bestAlternative = Iterables.getLast(alternatives);
        for (RouteAlternative alternative : alternatives) {
            alternative.setProbability(0);
            if (alternative.getDisutility() < bestAlternative.getDisutility()) {
                bestAlternative = alternative;
            }
        }
        bestAlternative.setProbability(1);
    }

}
